package ecg;

import dsl.S;
import dsl.Q;
import dsl.Query;

public class PeakDetection {

	// The curve length transformation:
	//
	// adjust: x[n] = raw[n] - 1024
	// smooth: y[n] = (x[n-2] + x[n-1] + x[n] + x[n+1] + x[n+2]) / 5
	// deriv: d[n] = (y[n+1] - y[n-1]) / 2
	// length: l[n] = t(d[n-w]) + ... + t(d[n+w]), where
	//         w = 20 (samples) and t(d) = sqrt(1.0 + d * d)

	public static Query<Integer,Double> qLength() {
		// adjust >> smooth >> deriv >> length
		Query<Integer, Double> adjust = Q.map(v -> (double) (v - 1024));
		Query<Double, Double> smooth = Q.pipeline(
			Q.map(v -> v / 5.0),
			Q.sWindowInv(5, 0.0, Double::sum, (a, b) -> a - b)
		);
		Query<Double, Double> deriv = Q.sWindow3((v1, v2, v3) -> (v3 - v1) / 2.0);
		Query<Double, Double> length = Q.pipeline(
			Q.map(d -> Math.sqrt(1.0 + d * d)),
			Q.sWindowInv(41, 0.0, (a, b) -> a + b, (a, b) -> a - b)
		);
		return Q.pipeline(adjust, smooth, deriv, length);
	}

	// In order to detect peaks we need both the raw (or adjusted)
	// signal and the signal given by the curve length transformation.
	// Use the datatype VTL and implement the class Detect.

	public static Query<Integer,VTL> qVTL() {
		// 1. Convert Integer to VT (v, ts)
		Query<Integer, VT> qVT = Q.scan(new VT(0, -1), (vt, v) -> new VT(v, vt.ts + 1));
		
		// 2. Parallel:
		//    - l: qLength (delay 46)
		//    - vt: ignore 23 (to align with center of windows)
		return Q.pipeline(qVT, Q.parallel(
			Q.pipeline(Q.map(vt -> vt.v), qLength()),
			Q.ignore(23),
			(l, vt) -> vt.extendl(l)
		));
	}

	public static Query<Integer,Long> qPeaks() {
		return Q.pipeline(qVTL(), new Detect());
	}

	public static void main(String[] args) {
		System.out.println("****************************************");
		System.out.println("***** Algorithm for Peak Detection *****");
		System.out.println("****************************************");
		System.out.println();

		Q.execute(Data.ecgStream("100.csv"), qPeaks(), S.printer());
	}

}