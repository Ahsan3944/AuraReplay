package com.ultraop.aurareplay.recording;

import com.ultraop.aurareplay.recording.snapshot.TickSnapshot;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Seekable recording container with independently compressed keyframe blocks and a byte index. */
public final class RecordingIndexedFile {
    private static final int MAGIC = 0x41555249; // AURI
    private static final int VERSION = 1;
    private static final int BLOCK_SIZE = RecordingBinaryCodec.KEYFRAME_INTERVAL_FOR_INDEX;

    private RecordingIndexedFile() { }

    public static void write(Path path, Recording recording) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(recording, "recording");
        List<Block> blocks = new ArrayList<>();
        RecordingBinaryCodec codec = new RecordingBinaryCodec();
        List<TickSnapshot> frames = recording.frames();
        for (int start = 0; start < frames.size(); start += BLOCK_SIZE) {
            int end = Math.min(frames.size(), start + BLOCK_SIZE);
            List<TickSnapshot> blockFrames = frames.subList(start, end);
            byte[] encoded = codec.encode(new Recording(recording.name(), recording.durationTicks(), blockFrames));
            blocks.add(new Block(start, blockFrames.getFirst().tick(), encoded));
        }

        long headerSize = 4 + 4 + 4 + 8 + 4 + 4 + stringSize(recording.name());
        long indexSize = (long) blocks.size() * (4 + 8 + 8 + 4);
        long offset = headerSize + indexSize;
        List<Entry> entries = new ArrayList<>(blocks.size());
        for (Block block : blocks) {
            entries.add(new Entry(block.frameStart(), block.tick(), offset, block.data().length));
            offset += block.data().length;
        }

        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path)))) {
            out.writeInt(MAGIC); out.writeInt(VERSION); out.writeInt(BLOCK_SIZE);
            out.writeLong(recording.durationTicks()); out.writeInt(frames.size()); out.writeInt(entries.size());
            writeString(out, recording.name());
            for (Entry entry : entries) { out.writeInt(entry.frameStart()); out.writeLong(entry.tick()); out.writeLong(entry.offset()); out.writeInt(entry.length()); }
            for (Block block : blocks) out.write(block.data());
        }
    }

    public static Recording read(Path path) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            Header header = readHeader(file);
            List<Entry> entries = readEntries(file, header.blockCount());
            RecordingBinaryCodec codec = new RecordingBinaryCodec();
            List<TickSnapshot> frames = new ArrayList<>(header.frameCount());
            for (Entry entry : entries) frames.addAll(codec.decode(readBlock(file, entry)).frames());
            if (frames.size() != header.frameCount()) throw new IOException("indexed recording frame count mismatch");
            return new Recording(header.name(), header.durationTicks(), frames);
        }
    }

    /** Loads only the compressed block containing the requested frame. */
    public static TickSnapshot readFrame(Path path, int frameIndex) throws IOException {
        if (frameIndex < 0) throw new IllegalArgumentException("frameIndex must be >= 0");
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            Header header = readHeader(file);
            if (frameIndex >= header.frameCount()) throw new IndexOutOfBoundsException("frameIndex=" + frameIndex);
            Entry entry = floorEntry(readEntries(file, header.blockCount()), frameIndex);
            Recording block = new RecordingBinaryCodec().decode(readBlock(file, entry));
            return block.frames().get(frameIndex - entry.frameStart());
        }
    }

    /** Loads only the compressed block containing the requested tick. */
    public static TickSnapshot readTick(Path path, long tick) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            Header header = readHeader(file);
            if (header.frameCount() == 0) throw new IllegalStateException("recording is empty");
            Entry entry = floorEntryByTick(readEntries(file, header.blockCount()), tick);
            Recording block = new RecordingBinaryCodec().decode(readBlock(file, entry));
            TickSnapshot result = block.frames().getFirst();
            for (TickSnapshot frame : block.frames()) { if (frame.tick() > tick) break; result = frame; }
            return result;
        }
    }

    private static Header readHeader(RandomAccessFile file) throws IOException {
        if (file.readInt() != MAGIC) throw new IOException("invalid indexed AuraReplay header");
        int version = file.readInt();
        if (version != VERSION) throw new IOException("unsupported indexed AuraReplay version: " + version);
        int blockSize = file.readInt();
        if (blockSize <= 0) throw new IOException("invalid indexed recording block size");
        long duration = file.readLong();
        int frames = checked(file.readInt());
        int blocks = checked(file.readInt());
        String name = readString(file);
        if (frames > 0 && blocks == 0) throw new IOException("indexed recording has no blocks");
        return new Header(name, blockSize, duration, frames, blocks);
    }

    private static List<Entry> readEntries(RandomAccessFile file, int count) throws IOException {
        List<Entry> result = new ArrayList<>(count);
        long previousOffset = Long.MIN_VALUE; int previousFrame = -1;
        for (int i = 0; i < count; i++) {
            int frameStart = checked(file.readInt()); long tick = file.readLong(); long offset = file.readLong(); int length = checked(file.readInt());
            if (frameStart <= previousFrame || offset < 0 || length <= 0 || (previousOffset != Long.MIN_VALUE && offset <= previousOffset)) throw new IOException("invalid indexed recording entry");
            result.add(new Entry(frameStart, tick, offset, length)); previousOffset = offset; previousFrame = frameStart;
        }
        return result;
    }

    private static byte[] readBlock(RandomAccessFile file, Entry entry) throws IOException {
        if (entry.offset() > file.length() || file.length() - entry.offset() < entry.length()) throw new EOFException("truncated indexed recording block");
        file.seek(entry.offset()); byte[] data = new byte[entry.length()]; file.readFully(data); return data;
    }

    private static Entry floorEntry(List<Entry> entries, int frame) { Entry result = entries.getFirst(); for (Entry entry : entries) { if (entry.frameStart() > frame) break; result = entry; } return result; }
    private static Entry floorEntryByTick(List<Entry> entries, long tick) { Entry result = entries.getFirst(); for (Entry entry : entries) { if (entry.tick() > tick) break; result = entry; } return result; }
    private static int checked(int value) throws IOException { if (value < 0 || value > 10_000_000) throw new IOException("invalid indexed recording count: " + value); return value; }
    private static int stringSize(String value) { return 4 + value.getBytes(StandardCharsets.UTF_8).length; }
    private static void writeString(DataOutputStream out, String value) throws IOException { byte[] bytes = value.getBytes(StandardCharsets.UTF_8); out.writeInt(bytes.length); out.write(bytes); }
    private static String readString(RandomAccessFile file) throws IOException { int length = checked(file.readInt()); byte[] bytes = new byte[length]; file.readFully(bytes); return new String(bytes, StandardCharsets.UTF_8); }

    private record Block(int frameStart, long tick, byte[] data) { }
    private record Entry(int frameStart, long tick, long offset, int length) { }
    private record Header(String name, int blockSize, long durationTicks, int frameCount, int blockCount) { }
}
