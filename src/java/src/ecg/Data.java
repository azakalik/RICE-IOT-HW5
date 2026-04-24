package ecg;

import java.io.FileNotFoundException;
import java.util.Iterator;

public class Data {

	// update to corresponding path
	private static final String PATH = "src/java/data/";

	private Data() {
		// nothing to do
	}

	public static Iterator<Integer> ecgStream(String file) {
		try {
			return new IteratorECG(PATH + file);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		return null;
	}
	
	public static void main(String[] args) {
		System.out.println("*********************************");
		System.out.println("********** ECG Dataset **********");
		System.out.println("*********************************");
		System.out.println();

		Iterator<Integer> it = Data.ecgStream("100-samples-100.csv");
		while (it.hasNext()) {
			System.out.println(it.next());
		}
	}

}
