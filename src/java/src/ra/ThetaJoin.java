package ra;

import java.util.function.BiPredicate;
import java.util.ArrayList;
import java.util.List;

import dsl.Query;
import dsl.Sink;
import utils.Or;
import utils.Pair;

// A streaming implementation of the theta join operator.
//
// We view the input as consisting of two channels:
// one with items of type A and one with items of type B.
// The output should contain all pairs (a, b) of input items,
// where a \in A is from the left channel, b \in B is from the
// right channel, and the pair (a, b) satisfies a predicate theta.

public class ThetaJoin<A,B> implements Query<Or<A,B>,Pair<A,B>> {

	private final BiPredicate<A,B> theta;
	private final List<A> leftBuffer;
	private final List<B> rightBuffer;

	private ThetaJoin(BiPredicate<A,B> theta) {
		this.theta = theta;
		this.leftBuffer = new ArrayList<>();
		this.rightBuffer = new ArrayList<>();
	}

	public static <A,B> ThetaJoin<A,B> from(BiPredicate<A,B> theta) {
		return new ThetaJoin<>(theta);
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
			leftBuffer.add(a);
			for (B b : rightBuffer) {
				if (theta.test(a, b)) {
					sink.next(Pair.from(a, b));
				}
			}
		} else {
			B b = item.getRight();
			rightBuffer.add(b);
			for (A a : leftBuffer) {
				if (theta.test(a, b)) {
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