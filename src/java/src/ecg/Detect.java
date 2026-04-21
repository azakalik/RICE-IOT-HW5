package ecg;

import dsl.Query;
import dsl.Sink;

// The detection algorithm (decision rule) that we described in class
// (or your own slight variant of it).
//
// (1) Determine the threshold using the class TrainModel.
//
// (2) When l[n] exceeds the threhold, search for peak (max x[n] or raw[n])
//     in the next 40 samples.
//
// (3) No peak should be detected for 72 samples after the last peak.
//
// OUTPUT: The timestamp of each peak.

public class Detect implements Query<VTL,Long> {

	// Choose this to be two times the average length
	// over the entire signal.
	private static final double THRESHOLD = 255.306;

	private boolean isSearching;
	private long searchEndTs;
	private int maxV;
	private long maxTs;
	
	private long refractoryUntil; // TS after which a new search can start

	public Detect() {
		this.isSearching = false;
		this.refractoryUntil = -1;
	}

	@Override
	public void start(Sink<Long> sink) {
		this.isSearching = false;
		this.refractoryUntil = -1;
	}

	@Override
	public void next(VTL item, Sink<Long> sink) {
		if (item.ts <= refractoryUntil) {
			return; // Refractory period
		}
		
		if (isSearching) {
			if (item.v > maxV) {
				maxV = item.v;
				maxTs = item.ts;
			}
			if (item.ts >= searchEndTs) {
				sink.next(maxTs);
				isSearching = false;
				refractoryUntil = maxTs + 72;
			}
		} else {
			if (item.l > THRESHOLD) {
				isSearching = true;
				searchEndTs = item.ts + 40;
				maxV = item.v;
				maxTs = item.ts;
			}
		}
	}

	@Override
	public void end(Sink<Long> sink) {
		if (isSearching) {
			sink.next(maxTs);
		}
		sink.end();
	}
	
}
