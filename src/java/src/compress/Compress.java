package compress;

import java.util.Iterator;

import dsl.*;
import ecg.Data;

public class Compress {

	public static final int BLOCK_SIZE = 10;
	private static final int HEADER_BITS = 4;
	private static final int MAX_ZIGZAG_VALUE = 510;
	private static final int HEADER_CONST = 10;
	private static final int HEADER_FIRST_THEN_CONST = 11;

	public static Query<Integer,Integer> delta() {
		return new Query<Integer,Integer>() {
			private int prev;

			@Override
			public void start(Sink<Integer> sink) {
				prev = 0;
			}

			@Override
			public void next(Integer item, Sink<Integer> sink) {
				int x = item;
				sink.next(x - prev);
				prev = x;
			}

			@Override
			public void end(Sink<Integer> sink) {
				// Nothing to flush.
			}
		};
	}

	public static Query<Integer,Integer> deltaInv() {
		return new Query<Integer,Integer>() {
			private int prev;

			@Override
			public void start(Sink<Integer> sink) {
				prev = 0;
			}

			@Override
			public void next(Integer item, Sink<Integer> sink) {
				int x = prev + item;
				sink.next(x);
				prev = x;
			}

			@Override
			public void end(Sink<Integer> sink) {
				// Nothing to flush.
			}
		};
	}

	public static Query<Integer,Integer> zigzag() {
		return Q.map(x -> x >= 0 ? 2 * x : -2 * x - 1);
	}

	public static Query<Integer,Integer> zigzagInv() {
		return Q.map(x -> (x % 2 == 0) ? (x / 2) : -((x + 1) / 2));
	}

	public static Query<Integer,Integer> pack() {
		return new Query<Integer,Integer>() {
			private final int[] block = new int[BLOCK_SIZE];
			private int count;

			@Override
			public void start(Sink<Integer> sink) {
				count = 0;
			}

			@Override
			public void next(Integer item, Sink<Integer> sink) {
				int x = item;
				if (x < 0 || x > MAX_ZIGZAG_VALUE) {
					throw new IllegalArgumentException("packed values must be in [0, 510]");
				}

				block[count++] = x;
				if (count == BLOCK_SIZE) {
					emitBlock(block, sink);
					count = 0;
				}
			}

			@Override
			public void end(Sink<Integer> sink) {
				if (count != 0) {
					throw new IllegalStateException("input length must be a multiple of BLOCK_SIZE");
				}
			}
		};
	}

	public static Query<Integer,Integer> unpack() {
		return new Query<Integer,Integer>() {
			private final int[] bytes = new int[16];
			private int count;
			private int expected;

			@Override
			public void start(Sink<Integer> sink) {
				count = 0;
				expected = -1;
			}

			@Override
			public void next(Integer item, Sink<Integer> sink) {
				int b = item;
				if (b < 0 || b > 255) {
					throw new IllegalArgumentException("compressed bytes must be in [0, 255]");
				}

				bytes[count++] = b;

				if (count == 1) {
					int header = b & 0x0F;
					expected = encodedBlockBytes(header);
				}

				if (count == expected) {
					emitUnpackedBlock(bytes, sink);
					count = 0;
					expected = -1;
				}
			}

			@Override
			public void end(Sink<Integer> sink) {
				if (count != 0) {
					throw new IllegalStateException("truncated compressed block");
				}
			}
		};
	}

	public static Query<Integer,Integer> compress() {
		return Q.pipeline(delta(), zigzag(), pack());
	}

	public static Query<Integer,Integer> decompress() {
		return Q.pipeline(unpack(), zigzagInv(), deltaInv());
	}

	private static void emitBlock(int[] block, Sink<Integer> sink) {
		if (allEqual(block)) {
			emitConstBlock(block[0], sink);
			return;
		}

		if (tailAllEqual(block)) {
			emitFirstThenConstBlock(block[0], block[1], sink);
			return;
		}

		int max = 0;
		for (int x : block) {
			max = Math.max(max, x);
		}

		int width = bitsRequired(max);
		int totalBits = HEADER_BITS + width * BLOCK_SIZE;
		int totalBytes = bytesRequired(totalBits);
		int[] out = new int[totalBytes];

		int bitPos = 0;
		bitPos = writeBits(out, bitPos, width, HEADER_BITS);
		for (int x : block) {
			bitPos = writeBits(out, bitPos, x, width);
		}

		emitBytes(out, sink);
	}

	private static void emitConstBlock(int value, Sink<Integer> sink) {
		int[] out = new int[bytesRequired(HEADER_BITS + 9)];
		int bitPos = 0;
		bitPos = writeBits(out, bitPos, HEADER_CONST, HEADER_BITS);
		writeBits(out, bitPos, value, 9);
		emitBytes(out, sink);
	}

	private static void emitFirstThenConstBlock(int first, int rest, Sink<Integer> sink) {
		int[] out = new int[bytesRequired(HEADER_BITS + 9 + 9)];
		int bitPos = 0;
		bitPos = writeBits(out, bitPos, HEADER_FIRST_THEN_CONST, HEADER_BITS);
		bitPos = writeBits(out, bitPos, first, 9);
		writeBits(out, bitPos, rest, 9);
		emitBytes(out, sink);
	}

	private static void emitUnpackedBlock(int[] bytes, Sink<Integer> sink) {
		int header = bytes[0] & 0x0F;
		int bitPos = HEADER_BITS;

		if (header == HEADER_CONST) {
			int value = readBits(bytes, bitPos, 9);
			for (int i = 0; i < BLOCK_SIZE; i++) {
				sink.next(value);
			}
			return;
		}

		if (header == HEADER_FIRST_THEN_CONST) {
			int first = readBits(bytes, bitPos, 9);
			bitPos += 9;
			int rest = readBits(bytes, bitPos, 9);

			sink.next(first);
			for (int i = 1; i < BLOCK_SIZE; i++) {
				sink.next(rest);
			}
			return;
		}

		int width = header;
		for (int i = 0; i < BLOCK_SIZE; i++) {
			int x = readBits(bytes, bitPos, width);
			bitPos += width;
			sink.next(x);
		}
	}

	private static int encodedBlockBytes(int header) {
		if (header >= 0 && header <= 9) {
			return bytesRequired(HEADER_BITS + header * BLOCK_SIZE);
		}
		if (header == HEADER_CONST) {
			return bytesRequired(HEADER_BITS + 9);
		}
		if (header == HEADER_FIRST_THEN_CONST) {
			return bytesRequired(HEADER_BITS + 9 + 9);
		}
		throw new IllegalArgumentException("invalid block header: " + header);
	}

	private static boolean allEqual(int[] block) {
		for (int i = 1; i < BLOCK_SIZE; i++) {
			if (block[i] != block[0]) {
				return false;
			}
		}
		return true;
	}

	private static boolean tailAllEqual(int[] block) {
		for (int i = 2; i < BLOCK_SIZE; i++) {
			if (block[i] != block[1]) {
				return false;
			}
		}
		return true;
	}

	private static int bitsRequired(int x) {
		if (x == 0) {
			return 0;
		}
		return 32 - Integer.numberOfLeadingZeros(x);
	}

	private static int bytesRequired(int bits) {
		return (bits + 7) / 8;
	}

	private static int writeBits(int[] out, int bitPos, int value, int width) {
		for (int i = 0; i < width; i++) {
			if (((value >>> i) & 1) != 0) {
				int p = bitPos + i;
				out[p / 8] |= 1 << (p % 8);
			}
		}
		return bitPos + width;
	}

	private static int readBits(int[] bytes, int bitPos, int width) {
		int value = 0;
		for (int i = 0; i < width; i++) {
			int p = bitPos + i;
			int bit = (bytes[p / 8] >>> (p % 8)) & 1;
			value |= bit << i;
		}
		return value;
	}

	private static void emitBytes(int[] out, Sink<Integer> sink) {
		for (int b : out) {
			sink.next(b & 0xFF);
		}
	}

	public static void main(String[] args) {
		System.out.println("**********************************************");
		System.out.println("***** ToyDSL & Compression/Decompression *****");
		System.out.println("**********************************************");
		System.out.println();

		System.out.println("***** Compress *****");
		{
			// from range [0,2048) to [0,256)
			Query<Integer,Integer> q1 = Q.map(x -> x / 8);
			Query<Integer,Integer> q2 = compress();
			Query<Integer,Integer> q = Q.pipeline(q1, q2);
			Iterator<Integer> it = Data.ecgStream("100-all.csv");
			Q.execute(it, q, S.lastCount());
		}
		System.out.println();

		System.out.println("***** Compress & Decompress *****");
		{
			// from range [0,2048) to [0,256)
			Query<Integer,Integer> q1 = Q.map(x -> x / 8);
			Query<Integer,Integer> q2 = compress();
			Query<Integer,Integer> q3 = decompress();
			Query<Integer,Integer> q = Q.pipeline(q1, q2, q3);
			Iterator<Integer> it = Data.ecgStream("100-all.csv");
			Q.execute(it, q, S.lastCount());
		}
		System.out.println();
	}

}
