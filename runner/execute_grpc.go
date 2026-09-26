package main

import (
	"context"
	"fmt"
	"io"
	"strings"
	"time"

	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/metadata"
	reflectionpb "google.golang.org/grpc/reflection/grpc_reflection_v1"
	reflectionv1alpha "google.golang.org/grpc/reflection/grpc_reflection_v1alpha"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/encoding/protojson"
	"google.golang.org/protobuf/proto"
	"google.golang.org/protobuf/reflect/protodesc"
	"google.golang.org/protobuf/reflect/protoreflect"
	"google.golang.org/protobuf/types/descriptorpb"
	"google.golang.org/protobuf/types/dynamicpb"
)

// executeGRPC calls a unary method it knows nothing about in advance: it asks the server for the
// method's descriptors (server reflection), builds the request from JSON, and returns JSON.
func (r *Runner) executeGRPC(ctx context.Context, job Job) Result {
	address := r.cfg.GRPCTargets[job.Target.Service]
	if address == "" {
		address = job.Target.Address
	}
	if address == "" {
		return failed(fmt.Errorf("don't know where %s's gRPC server is: set RUNNER_GRPC_TARGETS=%s=host:port", job.Target.Service, job.Target.Service))
	}
	conn, err := grpc.NewClient(address, grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		return failed(err)
	}
	defer conn.Close()

	method, err := resolveMethod(ctx, conn, job.Target.Method)
	if err != nil {
		return failed(err)
	}
	in := dynamicpb.NewMessage(method.Input())
	if strings.TrimSpace(job.Request.Body) != "" {
		if err := protojson.Unmarshal([]byte(job.Request.Body), in); err != nil {
			return failed(fmt.Errorf("request doesn't match %s: %w", method.Input().FullName(), err))
		}
	}
	md := metadata.New(job.Request.Headers)
	for k, v := range job.propagation() {
		md.Set(k, v)
	}
	out := dynamicpb.NewMessage(method.Output())
	start := time.Now()
	fullMethod := "/" + string(method.Parent().FullName()) + "/" + string(method.Name())
	callErr := conn.Invoke(metadata.NewOutgoingContext(ctx, md), fullMethod, in, out)
	duration := time.Since(start).Milliseconds()

	code := int(status.Code(callErr))
	if callErr != nil {
		return Result{Sent: true, Status: &code, Body: status.Convert(callErr).Message(), DurationMs: duration}
	}
	body, _ := protojson.Marshal(out)
	return Result{Sent: true, Status: &code, Body: truncate(body), DurationMs: duration}
}

func resolveMethod(ctx context.Context, conn *grpc.ClientConn, name string) (protoreflect.MethodDescriptor, error) {
	service, method, ok := strings.Cut(name, "/")
	if !ok {
		return nil, fmt.Errorf("gRPC method %q isn't package.Service/Method", name)
	}
	fetch, err := reflectionFetcher(ctx, conn)
	if err != nil {
		return nil, err
	}
	files := map[string]*descriptorpb.FileDescriptorProto{}
	queue := []fileQuery{{symbol: service}}
	for len(queue) > 0 {
		raws, err := fetch(queue[0])
		queue = queue[1:]
		if err != nil {
			return nil, err
		}
		for _, raw := range raws {
			fd := &descriptorpb.FileDescriptorProto{}
			if err := proto.Unmarshal(raw, fd); err != nil {
				return nil, err
			}
			if _, seen := files[fd.GetName()]; seen {
				continue
			}
			files[fd.GetName()] = fd
			// Every import comes from the server too, so the descriptors resolve on their own.
			for _, dep := range fd.GetDependency() {
				if _, seen := files[dep]; !seen {
					queue = append(queue, fileQuery{filename: dep})
				}
			}
		}
	}
	set := &descriptorpb.FileDescriptorSet{}
	for _, fd := range files {
		set.File = append(set.File, fd)
	}
	registry, err := protodesc.NewFiles(set)
	if err != nil {
		return nil, err
	}
	desc, err := registry.FindDescriptorByName(protoreflect.FullName(service))
	if err != nil {
		return nil, fmt.Errorf("service %s not found via reflection", service)
	}
	sd, ok := desc.(protoreflect.ServiceDescriptor)
	if !ok {
		return nil, fmt.Errorf("%s isn't a service", service)
	}
	md := sd.Methods().ByName(protoreflect.Name(method))
	if md == nil {
		return nil, fmt.Errorf("%s has no method %s", service, method)
	}
	if md.IsStreamingClient() || md.IsStreamingServer() {
		return nil, fmt.Errorf("%s is streaming; test runs support unary methods", name)
	}
	return md, nil
}

type fileQuery struct{ symbol, filename string }

// reflectionFetcher speaks server reflection v1, falling back to v1alpha — still the only version
// some servers (e.g. Python's grpcio-reflection) register.
func reflectionFetcher(ctx context.Context, conn *grpc.ClientConn) (func(fileQuery) ([][]byte, error), error) {
	v1, err := reflectionpb.NewServerReflectionClient(conn).ServerReflectionInfo(ctx)
	if err != nil {
		return nil, fmt.Errorf("server reflection unavailable: %w", err)
	}
	fetchV1 := func(q fileQuery) ([][]byte, error) {
		req := &reflectionpb.ServerReflectionRequest{MessageRequest: &reflectionpb.ServerReflectionRequest_FileContainingSymbol{FileContainingSymbol: q.symbol}}
		if q.filename != "" {
			req.MessageRequest = &reflectionpb.ServerReflectionRequest_FileByFilename{FileByFilename: q.filename}
		}
		// A rejected stream (e.g. Unimplemented) fails Send with io.EOF; Recv has the actual status.
		if err := v1.Send(req); err != nil && err != io.EOF {
			return nil, err
		}
		res, err := v1.Recv()
		if err != nil {
			return nil, err
		}
		if e := res.GetErrorResponse(); e != nil {
			return nil, fmt.Errorf("server reflection: %s", e.GetErrorMessage())
		}
		return res.GetFileDescriptorResponse().GetFileDescriptorProto(), nil
	}
	// Probe v1 with the first real query; fall back if the server doesn't implement it.
	first := true
	return func(q fileQuery) ([][]byte, error) {
		if !first {
			return fetchV1(q)
		}
		first = false
		raws, err := fetchV1(q)
		if status.Code(err) != codes.Unimplemented {
			return raws, err
		}
		alpha, err := reflectionv1alpha.NewServerReflectionClient(conn).ServerReflectionInfo(ctx)
		if err != nil {
			return nil, fmt.Errorf("server reflection unavailable: %w", err)
		}
		fetchAlpha := func(q fileQuery) ([][]byte, error) {
			req := &reflectionv1alpha.ServerReflectionRequest{MessageRequest: &reflectionv1alpha.ServerReflectionRequest_FileContainingSymbol{FileContainingSymbol: q.symbol}}
			if q.filename != "" {
				req.MessageRequest = &reflectionv1alpha.ServerReflectionRequest_FileByFilename{FileByFilename: q.filename}
			}
			// A rejected stream fails Send with io.EOF; Recv has the actual status.
			if err := alpha.Send(req); err != nil && err != io.EOF {
				return nil, err
			}
			res, err := alpha.Recv()
			if err != nil {
				return nil, err
			}
			if e := res.GetErrorResponse(); e != nil {
				return nil, fmt.Errorf("server reflection: %s", e.GetErrorMessage())
			}
			return res.GetFileDescriptorResponse().GetFileDescriptorProto(), nil
		}
		fetchV1 = fetchAlpha
		return fetchAlpha(q)
	}, nil
}
