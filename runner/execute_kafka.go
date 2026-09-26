package main

import (
	"context"
	"crypto/tls"
	"errors"
	"sync"
	"time"

	"github.com/twmb/franz-go/pkg/kgo"
	"github.com/twmb/franz-go/pkg/sasl/plain"
	"github.com/twmb/franz-go/pkg/sasl/scram"
)

type kafkaProducer struct {
	once   sync.Once
	client *kgo.Client
	err    error
}

func (k *kafkaProducer) get(cfg Config) (*kgo.Client, error) {
	k.once.Do(func() {
		if len(cfg.KafkaBrokers) == 0 {
			k.err = errors.New("messaging runs need KAFKA_BROKERS on the runner")
			return
		}
		opts := []kgo.Opt{kgo.SeedBrokers(cfg.KafkaBrokers...), kgo.ProducerLinger(0)}
		switch cfg.KafkaSASL {
		case "PLAIN":
			opts = append(opts, kgo.SASL(plain.Auth{User: cfg.KafkaUser, Pass: cfg.KafkaPassword}.AsMechanism()))
		case "SCRAM-SHA-256":
			opts = append(opts, kgo.SASL(scram.Auth{User: cfg.KafkaUser, Pass: cfg.KafkaPassword}.AsSha256Mechanism()))
		case "SCRAM-SHA-512":
			opts = append(opts, kgo.SASL(scram.Auth{User: cfg.KafkaUser, Pass: cfg.KafkaPassword}.AsSha512Mechanism()))
		}
		if cfg.KafkaTLS {
			opts = append(opts, kgo.DialTLSConfig(&tls.Config{MinVersion: tls.VersionTLS12}))
		}
		k.client, k.err = kgo.NewClient(opts...)
	})
	return k.client, k.err
}

func (r *Runner) executeKafka(ctx context.Context, job Job) Result {
	client, err := r.kafka.get(r.cfg)
	if err != nil {
		return failed(err)
	}
	record := &kgo.Record{Topic: job.Target.Topic, Value: []byte(job.Request.Body)}
	if job.Request.Key != "" {
		record.Key = []byte(job.Request.Key)
	}
	for k, v := range job.Request.Headers {
		record.Headers = append(record.Headers, kgo.RecordHeader{Key: k, Value: []byte(v)})
	}
	for k, v := range job.propagation() {
		record.Headers = append(record.Headers, kgo.RecordHeader{Key: k, Value: []byte(v)})
	}
	start := time.Now()
	produced, err := client.ProduceSync(ctx, record).First()
	if err != nil {
		return failed(err)
	}
	return Result{Sent: true, Partition: &produced.Partition, Offset: &produced.Offset, DurationMs: time.Since(start).Milliseconds()}
}
