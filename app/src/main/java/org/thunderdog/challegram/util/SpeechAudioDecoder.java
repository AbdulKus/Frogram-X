package org.thunderdog.challegram.util;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

final class SpeechAudioDecoder {
  static float[] decode (String path, AtomicBoolean cancelled) throws IOException {
    MediaExtractor extractor = new MediaExtractor();
    MediaCodec codec = null;
    try {
      extractor.setDataSource(path);
      MediaFormat format = null;
      for (int track = 0; track < extractor.getTrackCount(); track++) {
        MediaFormat candidate = extractor.getTrackFormat(track);
        String mime = candidate.getString(MediaFormat.KEY_MIME);
        if (mime != null && mime.startsWith("audio/")) {
          extractor.selectTrack(track);
          format = candidate;
          break;
        }
      }
      if (format == null) throw new IOException("No audio track");
      codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
      codec.configure(format, null, null, 0);
      codec.start();
      ByteBuffer[] inputs = codec.getInputBuffers();
      ByteBuffer[] outputs = codec.getOutputBuffers();
      int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
      int rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
      if (channels < 1 || channels > 8) throw new IOException("Unsupported channel count");
      int encoding = 2; // AudioFormat.ENCODING_PCM_16BIT
      boolean inputEnded = false;
      boolean outputEnded = false;
      PcmResampler pcm = new PcmResampler();
      MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
      long lastOutput = android.os.SystemClock.elapsedRealtime();
      while (!outputEnded && !cancelled.get()) {
        if (android.os.SystemClock.elapsedRealtime() - lastOutput > 30000) throw new IOException("Audio decoder stalled");
        if (!inputEnded) {
          int index = codec.dequeueInputBuffer(10000);
          if (index >= 0) {
            ByteBuffer input = inputs[index];
            input.clear();
            int count = extractor.readSampleData(input, 0);
            if (count < 0) {
              codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
              inputEnded = true;
            } else {
              codec.queueInputBuffer(index, 0, count, extractor.getSampleTime(), 0);
              extractor.advance();
            }
          }
        }
        int index = codec.dequeueOutputBuffer(info, 10000);
        if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) outputs = codec.getOutputBuffers();
        else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          MediaFormat decoded = codec.getOutputFormat();
          rate = decoded.getInteger(MediaFormat.KEY_SAMPLE_RATE);
          channels = decoded.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
          encoding = decoded.containsKey("pcm-encoding") ? decoded.getInteger("pcm-encoding") : 2;
          if (channels < 1 || channels > 8 || (encoding != 2 && encoding != 4)) throw new IOException("Unsupported PCM format");
        } else if (index >= 0) {
          lastOutput = android.os.SystemClock.elapsedRealtime();
          ByteBuffer buffer = outputs[index].duplicate().order(ByteOrder.LITTLE_ENDIAN);
          buffer.position(info.offset);
          buffer.limit(info.offset + info.size);
          int frameBytes = channels * (encoding == 4 ? 4 : 2);
          while (buffer.remaining() >= frameBytes) {
            float mono = 0;
            for (int channel = 0; channel < channels; channel++) mono += encoding == 4 ? buffer.getFloat() : buffer.getShort() / 32768f;
            pcm.add(mono / channels, rate);
          }
          outputEnded = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
          codec.releaseOutputBuffer(index, false);
        }
      }
      if (cancelled.get()) throw new IOException("Cancelled");
      return pcm.finish();
    } finally {
      if (codec != null) {
        try { codec.stop(); } catch (RuntimeException ignored) { }
        codec.release();
      }
      extractor.release();
    }
  }
}
