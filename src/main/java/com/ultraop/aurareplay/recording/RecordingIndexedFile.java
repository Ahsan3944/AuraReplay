package com.ultraop.aurareplay.recording;

import com.ultraop.aurareplay.recording.snapshot.TickSnapshot;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Seekable on-disk recording container. Each keyframe interval is an independently
 * compressed RecordingBinaryCodec block, while this container keeps a compact byte
 * index so a target frame can be loaded without decoding earlier blocks.
 */
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
            Recording blockRecording = new Recording(recording.name(), recording.durationTicks(), blockFrames);
            byte[] encoded = codec.encode(blockRecording);
            blocks.add(new Block(start, blockFrames.getFirst().tick(), encoded));
        }

        long headerSize = 4 + 4 + 4 + 8 + 4 + 4;
        long indexSize = 0;
        for (Block block : blocks) indexSize += 4 + 8 + 8 + 4;
        long offset = headerSize + indexSize;
        List<Entry> entries = new ArrayList<>(blocks.size());
        for (Block block : blocks) {
            entries.add(new Entry(block.frameStart(), block.tick(), offset, block.data().length));
            offset += block.data().length;
        }

        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path)))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(BLOCK_SIZE);
            out.writeLong(recording.durationTicks());
            out.writeInt(frames.size());
            out.writeInt(entries.size());
            for (Entry entry : entries) {
                out.writeInt(entry.frameStart());
                out.writeLong(entry.tick());
                out.writeLong(entry.offset());
                out.writeInt(entry.length());
            }
            for (Block block : blocks) out.write(block.data());
        }
    }

    public static Recording read(Path path) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            Header header = readHeader(file);
            List<Entry> entries = readEntries(file, header.blockCount());
            RecordingBinaryCodec codec = new RecordingBinaryCodec();
            List<TickSnapshot> frames = new ArrayList<>(header.frameCount());
            for (Entry entry : entries) {
                file.seek(entry.offset());
                byte[] data = new byte[entry.length()];
                file.readFully(data);
                Recording block = codec.decode(data);
                frames.addAll(block.frames());
            }
            if (frames.size() != header.frameCount()) throw new IOException("indexed recording frame count mismatch");
            return new Recording(readName(path), header.durationTicks(), frames);
        }
    }

    /** Loads only the block containing the requested frame. */
    public static TickSnapshot readFrame(Path path, int frameIndex) throws IOException {
        if (frameIndex < 0) throw new IllegalArgumentException("frameIndex must be >= 0");
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            Header header = readHeader(file);
            if (frameIndex >= header.frameCount()) throw new IndexOutOfBoundsException("frameIndex=" + frameIndex);
            List<Entry> entries = readEntries(file, header.blockCount());
            Entry entry = floorEntry(entries, frameIndex);
            file.seek(entry.offset());
            byte[] data = new byte[entry.length()];
            file.readFully(data);
            Recording block = new RecordingBinaryCodec().decode(data);
            return block.frames().get(frameIndex - entry.frameStart());
        }
    }

    /** Loads only the block containing the requested tick and returns the nearest frame at/before it. */
    public static TickSnapshot readTick(Path path, long tick) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            Header header = readHeader(file);
            if (header.frameCount() == 0) throw new IllegalStateException("recording is empty");
            List<Entry> entries = readEntries(file, header.blockCount());
            Entry entry = floorEntryByTick(entries, tick);
            file.seek(entry.offset());
            byte[] data = new byte[entry.length()];
            file.readFully(data);
            Recording block = new RecordingBinaryCodec().decode(data);
            TickSnapshot result = block.frames().getFirst();
            for (TickSnapshot frame : block.frames()) {
                if (frame.tick() > tick) break;
                result = frame;
            }
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
        if (frames > 0 && blocks == 0) throw new IOException("indexed recording has no blocks");
        return new Header(blockSize, duration, frames, blocks);
    }

    private static List<Entry> readEntries(RandomAccessFile file, int count) throws IOException {
        List<Entry> result = new ArrayList<>(count);
        long previousOffset = Long.MIN_VALUE;
        for (int i = 0; i < count; i++) {
            int frameStart = checked(file.readInt());
            long tick = file.readLong();
            long offset = file.readLong();
            int length = checked(file.readInt());
            if (offset < 0 || length <= 0 || (previousOffset != Long.MIN_VALUE && offset <= previousOffset)) throw new IOException("invalid indexed recording entry");
            result.add(new Entry(frameStart, tick, offset, length));
            previousOffset = offset;
        }
        return result;
    }

    private static Entry floorEntry(List<Entry> entries, int frame) {
        Entry result = entries.getFirst();
        for (Entry entry : entries) {
            if (entry.frameStart() > frame) break;
            result = entry;
        }
        return result;
    }

    private static Entry floorEntryByTick(List<Entry> entries, long tick) {
        Entry result = entries.getFirst();
        for (Entry entry : entries) {
            if (entry.tick() > tick) break;
            result = entry;
        }
        return result;
    }

    private static int checked(int value) throws IOException {
        if (value < 0 || value > 10_000_000) throw new IOException("invalid indexed recording count: " + value);
        return value;
    }

    private static String readName(Path path) {
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private record Block(int frameStart, long tick, byte[] data) { }
    private record Entry(int frameStart, long tick, long offset, int length) { }
    private record Header(int blockSize, long durationTicks, int frameCount, int blockCount) { }
}
