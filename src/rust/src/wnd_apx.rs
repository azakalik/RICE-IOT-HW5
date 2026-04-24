use super::*;

const MEM_SIZE: usize = 900; // DO NOT CHANGE

// This file contains the implementation of an approximation algorithm
// for the sliding-window average.
//
// We have to make sure that we do not use more than `MEM_SIZE`
// bytes of memory for the state of our streaming algorithm.
//

// State of the streaming algorithm
pub struct WndApx {
	// DO NOT MAKE ANY CHANGE HERE
	ram: [u8; MEM_SIZE], // memory contents
	// DO NOT MAKE ANY CHANGE HERE
}

// TODO: If needed, you can add functions here.

fn encode(s: u16) -> u8 {
	if s < 20 {
		s as u8
	} else if s < 60 {
		20 + ((s - 20) >> 1) as u8
	} else if s < 140 {
		40 + ((s - 60) >> 2) as u8
	} else if s < 300 {
		60 + ((s - 140) >> 3) as u8
	} else if s < 620 {
		80 + ((s - 300) >> 4) as u8
	} else {
		100 + ((s - 620) >> 5) as u8
	}
}

fn decode(c: u8) -> u16 {
	if c < 20 {
		c as u16
	} else if c < 40 {
		20 + ((c as u16 - 20) << 1)
	} else if c < 60 {
		60 + ((c as u16 - 40) << 2)
	} else if c < 80 {
		140 + ((c as u16 - 60) << 3)
	} else if c < 100 {
		300 + ((c as u16 - 80) << 4)
	} else {
		620 + ((c as u16 - 100) << 5)
	}
}

impl WndApx {
	pub fn new() -> Self {
		// DO NOT MAKE ANY CHANGE HERE
		Self {
			ram: [0; MEM_SIZE],
		}
		// DO NOT MAKE ANY CHANGE HERE
	}

	// --- Safe RAM Accessors ---

	// Read/write the circular buffer's head pointer at offset 880
	fn get_head(&self) -> u16 {
		u16::from_le_bytes([self.ram[880], self.ram[881]])
	}

	fn set_head(&mut self, h: u16) {
		let b = h.to_le_bytes();
		self.ram[880] = b[0];
		self.ram[881] = b[1];
	}

	// Read/write the 32-bit running sum at offset 882
	fn get_sum(&self) -> u32 {
		u32::from_le_bytes([self.ram[882], self.ram[883], self.ram[884], self.ram[885]])
	}

	fn set_sum(&mut self, s: u32) {
		let b = s.to_le_bytes();
		self.ram[882..886].copy_from_slice(&b);
	}
}

impl Query for WndApx {
	fn start<S: Sink>(&mut self, _sink: &mut S) {
		// Zeroing the entire ram correctly resets the buffer, head, and sum
		self.ram.fill(0);
	}

	fn next<S: Sink>(&mut self, item: u16, sink: &mut S) {
		assert!(item < LIMIT_SAMPLE);

		let head = self.get_head();
		let mut sum = self.get_sum();

		// 1. Bitwise addressing
		let bit_idx = (head as usize) * 7;
		let byte_idx = bit_idx / 8;
		let bit_offset = bit_idx % 8;

		// 2. Read old 7-bit compressed code from packed buffer
		let old_word = u16::from_le_bytes([self.ram[byte_idx], self.ram[byte_idx + 1]]);
		let c_old = ((old_word >> bit_offset) & 0x7F) as u8;

		// 3. Remove old value from sum, add new value
		let s_old = decode(c_old);
		sum -= s_old as u32;

		let c_new = encode(item);
		let s_new = decode(c_new);
		sum += s_new as u32;

		// 4. Insert the new 7-bit compressed code back into the packed buffer
		let mut new_word = old_word & !(0x7F << bit_offset);
		new_word |= (c_new as u16) << bit_offset;
		let bytes = new_word.to_le_bytes();
		self.ram[byte_idx] = bytes[0];
		self.ram[byte_idx + 1] = bytes[1];

		// 5. Advance window
		let mut new_head = head + 1;
		if new_head as usize == WND_SIZE {
			new_head = 0;
		}

		// 6. Update state
		self.set_head(new_head);
		self.set_sum(sum);

		// 7. Fire output tuple expected by the trait
		let q = (sum / (WND_SIZE as u32)) as u16;
		let r = (sum % (WND_SIZE as u32)) as u16;
		sink.next((q, r));
	}

	fn end<S: Sink>(&mut self, sink: &mut S) {
		sink.end();
	}
}

// cargo test -- --nocapture --test-threads=1
// cargo test --release -- --nocapture test_wnd_apx_0
// cargo test --release -- --nocapture test_wnd_apx_1
// cargo test --release -- --nocapture test_wnd_apx_2
// cargo test --release -- --nocapture test_wnd_apx_3
#[cfg(test)]
mod tests {
	use super::*;
	use crate::wnd_exact::WndExact;

	#[test]
	fn test_wnd_apx_3() {
		println!("\n");
		println!("***** Approximate Algorithm for Sliding Average *****");
		println!();

		let mut max_rel_error = 0.0_f64;
		for value in 0..LIMIT_SAMPLE {
			let mut sink = sink::SLast::new();
			let mut query = WndExact::new();
			let mut sink_apx = sink::SLast::new();
			let mut query_apx = WndApx::new();
			query.start(&mut sink);
			query_apx.start(&mut sink_apx);

			let it = {
				// constant stream
				core::iter::repeat(value).take(1000).enumerate()
			};
			for (i, item) in it {
				println!("i = {}, item = {}", i, item);
				query.next(item, &mut sink);
				let last = sink.last().unwrap();
				let last = number_u32(last);
				query_apx.next(item, &mut sink_apx);
				let last_apx = sink_apx.last().unwrap();
				let last_apx = number_u32(last_apx);
				let abs_error = last - last_apx;
				println!(
					"  sum: value = {}, estimate = {}, abs. error = {}",
					last, last_apx, abs_error
				);
				assert!(K * abs_error <= last);
				let wnd_size = u16::try_from(WND_SIZE).unwrap();
				let wnd_size = f64::from(wnd_size);
				let last = f64::from(last) / wnd_size;
				let last_apx = f64::from(last_apx) / wnd_size;
				let rel_error = 100.0 * (last - last_apx) / last;
				println!(
					"  avg: value = {:.3}, estimate = {:.3}, rel. error = {:.2}%",
					last, last_apx, rel_error
				);
				if last > 0.0 {
					assert!(rel_error <= EPS_P + 0.000001);
				}
				if !rel_error.is_nan() {
					max_rel_error = max_rel_error.max(rel_error);
				}
				println!();
			}
			query.end(&mut sink);
			query_apx.end(&mut sink_apx);
		}

		println!("maximum relative error = {}", max_rel_error);
		println!();
	}
	
	#[test]
	fn test_wnd_apx_2() {
		println!("\n");
		println!("***** Approximate Algorithm for Sliding Average *****");
		println!();

		let mut sink = sink::SLast::new();
		let mut query = WndExact::new();
		let mut sink_apx = sink::SLast::new();
		let mut query_apx = WndApx::new();
		query.start(&mut sink);
		query_apx.start(&mut sink_apx);

		let n = 10_000;
		let it = {
			(0..LIMIT_SAMPLE).cycle().take(n).enumerate()
		};
		let mut max_rel_error = 0.0_f64;
		for (i, item) in it {
			println!("i = {}, item = {}", i, item);
			query.next(item, &mut sink);
			let last = sink.last().unwrap();
			let last = number_u32(last);
			query_apx.next(item, &mut sink_apx);
			let last_apx = sink_apx.last().unwrap();
			let last_apx = number_u32(last_apx);
			let abs_error = last - last_apx;
			println!(
				"  sum: value = {}, estimate = {}, abs. error = {}",
				last, last_apx, abs_error
			);
			assert!(K * abs_error <= last);
			let wnd_size = u16::try_from(WND_SIZE).unwrap();
			let wnd_size = f64::from(wnd_size);
			let last = f64::from(last) / wnd_size;
			let last_apx = f64::from(last_apx) / wnd_size;
			let rel_error = 100.0 * (last - last_apx) / last;
			println!(
				"  avg: value = {:.3}, estimate = {:.3}, rel. error = {:.2}%",
				last, last_apx, rel_error
			);
			if last > 0.0 {
				assert!(rel_error <= EPS_P + 0.000001);
			}
			if !rel_error.is_nan() {
				max_rel_error = max_rel_error.max(rel_error);
			}
			println!();
		}
		query.end(&mut sink);
		query_apx.end(&mut sink_apx);

		println!("maximum relative error = {:.2}", max_rel_error);
		println!();
	}
	
	#[test]
	fn test_wnd_apx_1() {
		println!("\n");
		println!("***** Approximate Algorithm for Sliding Average *****");
		println!();

		// Used in the reference solution for testing individual components
		// of the algorithm.
	}

	#[test]
	fn test_wnd_apx_0() {
		println!("\n");
		println!("***** Approximate Algorithm for Sliding Average *****");
		println!();

		let name = core::any::type_name::<WndApx>();
		let size = core::mem::size_of::<WndApx>();
		assert_eq!(size, MEM_SIZE);
		println!("size of {} = {} bytes", name, size);
		println!();
	}
	
}
