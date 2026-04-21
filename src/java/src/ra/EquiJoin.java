package ra;

import java.util.function.Function;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dsl.Query;
import dsl.Sink;
import utils.Or;
import utils.Pair;

// A streaming implementation of the equi-join operator.
//
// We view the input as consisting of two channels:
// one with items of type A and one with items of type B.
// The output should contain all pairs (a, b) of input items,
// where a \in A is from the left channel, b \in B is from the
// right channel, and the equality predicate f(a) = g(b) holds.

public class EquiJoin<A,B,T> implements Query<Or<A,B>,Pair<A,B>> {

	private final Function<A,T> f;
	private final Function<B,T> g;
	private final Map<T, List<A>> leftBuffer;
	private final Map<T, List<B>> rightBuffer;

	private EquiJoin(Function<A,T> f, Function<B,T> g) {
		this.f = f;
		this.g = g;
		this.leftBuffer = new HashMap<>();
		this.rightBuffer = new HashMap<>();
	}

	public static <A,B,T> EquiJoin<A,B,T> from(Function<A,T> f, Function<B,T> g) {
		return new EquiJoin<>(f, g);
	}

	@Override
	public void start(Sink<Pair<A,B>> sink) {
		leftBuffer.clear();
		rightBuffer.clear();
	}

	@Override
	public void next(Or<A,B> item, Sink<Pair<A,B>> sink) {
		if (item.isLeft()) {
			A a = item.getLeft();
			T key = f.apply(a);
			leftBuffer.computeIfAbsent(key, k -> new ArrayList<>()).add(a);
			List<B> bs = rightBuffer.get(key);
			if (bs != null) {
				for (B b : bs) {
					sink.next(Pair.from(a, b));
				}
			}
		} else {
			B b = item.getRight();
			T key = g.apply(b);
			rightBuffer.computeIfAbsent(key, k -> new ArrayList<>()).add(b);
			List<A> as = leftBuffer.get(key);
			if (as != null) {
				for (A a : as) {
					sink.next(Pair.from(a, b));
				}
			}
		}
	}

	@Override
	public void end(Sink<Pair<A,B>> sink) {
		sink.end();
	}
	
}