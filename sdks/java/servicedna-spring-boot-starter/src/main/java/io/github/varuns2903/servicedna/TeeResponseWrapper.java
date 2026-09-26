package io.github.varuns2903.servicedna;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;

/**
 * Keeps a copy of the first {@code limit} bytes of a response while writing it straight through,
 * so streaming responses still stream and nothing is held back from the client.
 */
class TeeResponseWrapper extends HttpServletResponseWrapper {

  private final ByteArrayOutputStream copy = new ByteArrayOutputStream();
  private final int limit;
  private ServletOutputStream stream;
  private PrintWriter writer;

  TeeResponseWrapper(HttpServletResponse response, int limit) {
    super(response);
    this.limit = limit;
  }

  /** Pushes out what the application wrote through {@link #getWriter()}; the container can't see this writer. */
  void flushWriter() {
    if (writer != null) {
      writer.flush();
    }
  }

  byte[] copied() {
    flushWriter();
    return copy.toByteArray();
  }

  @Override
  public ServletOutputStream getOutputStream() throws IOException {
    if (stream == null) {
      ServletOutputStream target = super.getOutputStream();
      stream = new ServletOutputStream() {
        @Override
        public void write(int b) throws IOException {
          target.write(b);
          if (copy.size() < limit) {
            copy.write(b);
          }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
          target.write(b, off, len);
          int room = limit - copy.size();
          if (room > 0) {
            copy.write(b, off, Math.min(len, room));
          }
        }

        @Override
        public void flush() throws IOException {
          target.flush();
        }

        @Override
        public boolean isReady() {
          return target.isReady();
        }

        @Override
        public void setWriteListener(WriteListener listener) {
          target.setWriteListener(listener);
        }
      };
    }
    return stream;
  }

  @Override
  public PrintWriter getWriter() throws IOException {
    if (writer == null) {
      String encoding = getCharacterEncoding();
      writer = new PrintWriter(new OutputStreamWriter(getOutputStream(), encoding != null ? Charset.forName(encoding) : Charset.defaultCharset()));
    }
    return writer;
  }

  @Override
  public void flushBuffer() throws IOException {
    if (writer != null) {
      writer.flush();
    }
    super.flushBuffer();
  }
}
