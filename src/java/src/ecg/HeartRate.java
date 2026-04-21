package ecg;

import dsl.S;
import dsl.Q;
import dsl.Query;

// This file is devoted to the analysis of the heart rate of the patient.
// It is assumed that PeakDetection.qPeaks() has already been implemented.

public class HeartRate {

	// RR interval length (in milliseconds)
	public static Query<Integer,Double> qIntervals() {
		return Q.pipeline(PeakDetection.qPeaks(), Q.sWindow2((ts1, ts2) -> (ts2 - ts1) * 1000.0 / 360.0));
	}

	// Average heart rate (over entire signal) in bpm.
	public static Query<Integer,Double> qHeartRateAvg() {
		return Q.pipeline(qIntervals(), Q.foldAvg(), Q.map(avg -> 60000.0 / avg));
	}

	// Standard deviation of NN interval length (over the entire signal)
	// in milliseconds.
	public static Query<Integer,Double> qSDNN() {
		return Q.pipeline(qIntervals(), Q.foldStdev());
	}

	// RMSSD measure (over the entire signal) in milliseconds.
	public static Query<Integer,Double> qRMSSD() {
		return Q.pipeline(
			qIntervals(),
			Q.sWindow2((rr1, rr2) -> {
				double diff = rr2 - rr1;
				return diff * diff;
			}),
			Q.foldAvg(),
			Q.map(Math::sqrt)
		);
	}

	// Proportion (in %) derived by dividing NN50 by the total number
	// of NN intervals (calculated over the entire signal).
	public static Query<Integer,Double> qPNN50() {
		return Q.pipeline(
			qIntervals(),
			Q.sWindow2((rr1, rr2) -> Math.abs(rr2 - rr1) > 50.0 ? 1.0 : 0.0),
			Q.foldAvg(),
			Q.map(avg -> avg * 100.0)
		);
	}

	public static void main(String[] args) {
		System.out.println("****************************************");
		System.out.println("***** Algorithm for the Heart Rate *****");
		System.out.println("****************************************");
		System.out.println();

		System.out.println("***** Intervals *****");
		Q.execute(Data.ecgStream("100.csv"), qIntervals(), S.printer());
		System.out.println();

		System.out.println("***** Average heart rate *****");
		Q.execute(Data.ecgStream("100-all.csv"), qHeartRateAvg(), S.printer());
		System.out.println();

		System.out.println("***** HRV Measure: SDNN *****");
		Q.execute(Data.ecgStream("100-all.csv"), qSDNN(), S.printer());
		System.out.println();

		System.out.println("***** HRV Measure: RMSSD *****");
		Q.execute(Data.ecgStream("100-all.csv"), qRMSSD(), S.printer());
		System.out.println();

		System.out.println("***** HRV Measure: pNN50 *****");
		Q.execute(Data.ecgStream("100-all.csv"), qPNN50(), S.printer());
		System.out.println();
	}

}