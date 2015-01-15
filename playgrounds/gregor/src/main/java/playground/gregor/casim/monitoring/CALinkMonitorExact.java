package playground.gregor.casim.monitoring;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.collections.Tuple;

import playground.gregor.casim.simulation.physics.AbstractCANetwork;
import playground.gregor.casim.simulation.physics.CALink;
import playground.gregor.casim.simulation.physics.CAMoveableEntity;

public class CALinkMonitorExact implements Monitor {

	// private CALink l;
	private CAMoveableEntity[] parts;
	private int from;
	private int to;

	private double realRange;

	private List<Measure> ms = new ArrayList<Measure>();
	private double lastTriggered = -1;

	private final double h;
	private double cellWidth;

	private final Map<Id, AgentInfo> ais = new HashMap<>();
	private BufferedWriter spaceTimePlotter;

	private List<CAMoveableEntity> agents = new ArrayList<>();
	private CALink l;
	private double lastSpaceTimeSeriesReport = 0;
	private CAMoveableEntity[] parts2;

	public CALinkMonitorExact(CALink l, double range, CAMoveableEntity[] parts,
			double laneWidth) {
		this.l = l;
		this.parts = parts;
		int num = l.getNumOfCells();
		this.cellWidth = l.getLink().getLength() / num;
		double cells = range / cellWidth;
		this.from = (int) (num / 2. - cells / 2 + .5);
		this.to = (int) (num / 2. + cells / 2 + .5);
		this.h = (to - from) / 4.;
		this.realRange = (1 + to - from) * cellWidth;

	}

	public void addSpaceTimePlotter(BufferedWriter bw, CAMoveableEntity[] parts2) {
		this.spaceTimePlotter = bw;
		this.parts2 = parts2;
	}

	public void init() {

		for (int i = this.from; i <= this.to; i++) {
			CAMoveableEntity p = this.parts[i];
			if (p != null) {
				AgentInfo ai = new AgentInfo(p.getDir(), 0);
				ai.lastPosTime = 0;
				ai.lastpos = i;
				this.ais.put(p.getId(), ai);
				if (this.spaceTimePlotter != null) {
					this.agents.add(p);
				}
			}
		}

		if (this.spaceTimePlotter != null) {
			for (int i = 0; i < this.parts.length; i++) {
				CAMoveableEntity p = this.parts[i];
				if (p != null) {
					this.agents.add(p);
				}
			}
			// unidir exp
			for (int i = 0; i < this.parts2.length; i++) {
				CAMoveableEntity p = this.parts2[i];
				if (p != null) {
					this.agents.add(p);
				}
			}

		}
		trigger(0);
	}

	/* (non-Javadoc)
	 * @see playground.gregor.casim.monitoring.Monitor#trigger(double)
	 */
	@Override
	public void trigger(double time) {
		if (time <= lastTriggered) {
			return;
		}
		if (this.spaceTimePlotter != null
				&& time > lastSpaceTimeSeriesReport + 1 && time <= 600) {
			reportSpaceTimeSeries(time);
			lastSpaceTimeSeriesReport = time;
		}
		lastTriggered = time;
		// 1. check density
		int cnt = 0;
		// int dsCnt = 0;
		// int usCnt = 0;

		if (this.parts[this.from - 1] != null
				&& this.parts[this.from - 1].getDir() == 1) {
			AgentInfo ai = this.ais.get(this.parts[this.from - 1].getId());
			if (ai == null) {
				ai = new AgentInfo(1, time);
				ai.lastPosTime = time;
				ai.lastpos = this.from - 1;
				this.ais.put(this.parts[this.from - 1].getId(), ai);
			}

		}
		if (this.parts[this.to + 1] != null
				&& this.parts[this.to + 1].getDir() == -1) {
			AgentInfo ai = this.ais.get(this.parts[this.to + 1].getId());
			if (ai == null) {
				ai = new AgentInfo(-1, time);

				ai.lastPosTime = time;
				ai.lastpos = this.to + 1;
				this.ais.put(this.parts[this.to + 1].getId(), ai);
			}

		}

		{
			CAMoveableEntity part = this.parts[0];
			if (part != null && part.getDir() == -1) {
				AgentInfo ai = this.ais.remove(part.getId());
			}
		}
		{
			CAMoveableEntity part = this.parts[this.parts.length - 1];
			if (part != null && part.getDir() == 1) {
				AgentInfo ai = this.ais.remove(part.getId());
			}
		}

		Measure m = new Measure(time);
		for (int i = 1; i <= this.parts.length - 2; i++) {
			if (this.parts[i] != null) {
				AgentInfo ai = this.ais.get(this.parts[i].getId());
				if (ai != null && ai.lastpos != i) {
					// double tt = time - ai.lastLastLastPosTime;
					// ai.lastLastLastPosTime = ai.lastLastPosTime;
					// ai.lastLastPosTime = ai.lastPosTime;
					// ai.lastPosTime = time;
					ai.lastpos = i;
					ai.timePos.put(time, i);
				}
			}
		}

		double dsRho = 0;
		double usRho = 0;
		double rho = 0;
		double usCnt = 0;
		double dsCnt = 0;
		int center = (this.from + this.to) / 2;
		// int spdRange = (to - 2) - (from + 2) - 1;
		// double spdH = spdRange / 4.;
		for (int i = this.from; i <= this.to; i++) {
			if (this.parts[i] != null) {

				AgentInfo ai = this.ais.get(this.parts[i].getId());
				rho += AbstractCANetwork.RHO_HAT
						* a1DBSplineKernel(Math.abs(i - center), this.h);
				if (this.parts[i].getDir() == 1) {
					// if (i > from + 10 && i < to - 10) {
					m.dsAis.add(ai);
					// }
					// * a1DBSplineKernel(Math.abs(i - center), this.h);
					dsRho += AbstractCANetwork.RHO_HAT
							* a1DBSplineKernel(Math.abs(i - center), this.h);
				} else {
					// if (i > from + 10 && i < to - 10) {
					m.usAis.add(ai);
					// }
					// * a1DBSplineKernel(Math.abs(i - center), this.h);
					usRho += AbstractCANetwork.RHO_HAT
							* a1DBSplineKernel(Math.abs(i - center), this.h);
				}
			}
		}

		m.dsRho = dsRho;
		m.usRho = usRho;
		if (time > 1 && this.parts[center] != null) {
			this.ms.add(m);
		}
	}

	private void reportSpaceTimeSeries(double time) {

		try {
			this.spaceTimePlotter.append(time + " ");
			for (CAMoveableEntity a : this.agents) {
				if (a.getCurrentCANetworkEntity() == this.l) {
					this.spaceTimePlotter.append(a.getPos() + " ");
				} else {
					this.spaceTimePlotter.append("0 ");
				}
			}
			this.spaceTimePlotter.append("\n");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

	}

	/* (non-Javadoc)
	 * @see playground.gregor.casim.monitoring.Monitor#report(java.io.BufferedWriter)
	 */
	@Override
	public void report(BufferedWriter bw) throws IOException {

		for (Measure m : this.ms) {
			cmptSpd(m);
			// bw.append(m.time + " " + m.dsRho + " " + m.dsSpd + " " + m.usRho
			// + " " + m.usSpd + "\n");
		}
		// if (true) {
		// return;
		// }

		double dsRho = -1;
		double usRho = -1;
		double rho = -1;
		double dsSpd = -1;
		double usSpd = -1;
		List<Tuple<Integer, Integer>> ranges = new ArrayList<Tuple<Integer, Integer>>();
		int from = 0;
		int to = 0;
		// double mxDiff = 0.075;
		double mxDiff = 0.15;
		int mnRange = 5;
		double oldT = 0;
		int cnt = 0;
		for (Measure m : this.ms) {
			cnt++;
			// bw.append(m.time + " " + m.dsRho + " " + m.dsSpd + " " + m.usRho
			// + " " + m.usSpd + "\n");
			double usRhoDiff = Math.abs(usRho - m.usRho);
			double dsRhoDiff = Math.abs(dsRho - m.dsRho);
			double rhoDiff = Math.abs(rho - (m.dsRho + m.usRho));
			double dsSpdDiff = Math.abs(dsSpd - m.dsSpd);
			double usSpdDiff = Math.abs(usSpd - m.usSpd);
			// if (rhoDiff > rho * mxDiff || usRhoDiff > usRho * mxDiff
			// || dsRhoDiff > dsRho * mxDiff || dsSpdDiff > dsSpd * mxDiff
			// || usSpdDiff > usSpd * mxDiff || cnt == this.ms.size()) {
			double blancing = m.usRho / m.dsRho;
			if (dsSpdDiff > dsSpd * mxDiff || dsRhoDiff > dsRho * mxDiff
					|| usSpdDiff > usSpd * mxDiff || usRhoDiff > usRho * mxDiff
					|| cnt == this.ms.size() || (m.time - oldT) >= mnRange) {
				int range = to - from;
				if ((m.time - oldT) >= mnRange && !Double.isNaN(blancing)
						&& blancing <= 1.2 && blancing >= 0.83333) {
					ranges.add(new Tuple<Integer, Integer>(from, to));
				}
				oldT = m.time;
				from = to;
				dsRho = m.dsRho;
				usRho = m.usRho;
				rho = (m.usRho + m.dsRho);
				usSpd = m.usSpd;
				dsSpd = m.dsSpd;
			}

			to++;
		}
		int ccnt = 0;
		int cccnt = 0;
		for (Tuple<Integer, Integer> t : ranges) {
			// if (ccnt++ <= ranges.size() / 2 || ccnt > ranges.size() / 1.75
			// || cccnt++ > 10) {
			// continue; // skip first since it is 0
			// }
			// if (ccnt++ > 10 || ccnt > 100) {
			// continue;
			// }
			double range = t.getSecond() - t.getFirst();
			Measure m = new Measure(0);
			for (int i = t.getFirst(); i < t.getSecond(); i++) {
				Measure c = this.ms.get(i);
				m.dsRho += c.dsRho / range;
				m.dsSpd += c.dsSpd / range;
				m.usRho += c.usRho / range;
				m.usSpd += c.usSpd / range;
				m.time += c.time / range;
			}
			bw.append(m.time + " " + m.dsRho + " " + m.dsSpd + " " + m.usRho
					+ " " + m.usSpd + "\n");
		}
		bw.flush();
		// for (Measure m : this.ms){
		// // if (m.time<20 || m.time >100) {
		// //// return;
		// // continue;
		// // }
		// bw.append(m.time + " " + m.dsRho + " " + m.dsSpd + " " + m.usRho +
		// " " + m.usSpd +"\n");
		// }

	}

	private void cmptSpd(Measure m) {
		double dsSpd = 0;
		double time = m.time;
		int cnt = 0;
		for (AgentInfo ai : m.dsAis) {
			Entry<Double, Integer> floor = ai.timePos.floorEntry(time - 1.5);
			Entry<Double, Integer> ceil = ai.timePos.ceilingEntry(time + 1.5);
			if (floor == null || ceil == null) {
				continue;
			}
			cnt++;
			double tt = ceil.getKey() - floor.getKey();
			double dist = (ceil.getValue() - floor.getValue())
					/ (AbstractCANetwork.PED_WIDTH * AbstractCANetwork.RHO_HAT);
			dsSpd += (dist / tt);
		}
		if (cnt > 0)
			dsSpd /= cnt;
		m.dsSpd = dsSpd;

		double usSpd = 0;
		cnt = 0;
		for (AgentInfo ai : m.usAis) {
			Entry<Double, Integer> floor = ai.timePos.floorEntry(time - 1.5);
			Entry<Double, Integer> ceil = ai.timePos.ceilingEntry(time + 1.5);
			if (floor == null || ceil == null) {
				continue;
			}
			cnt++;
			double tt = ceil.getKey() - floor.getKey();
			double dist = (ceil.getValue() - floor.getValue())
					/ (AbstractCANetwork.PED_WIDTH * AbstractCANetwork.RHO_HAT);
			usSpd -= (dist / tt);
		}
		if (cnt > 0)
			usSpd /= cnt;
		m.usSpd = usSpd;

	}

	private static final class AgentInfo {
		TreeMap<Double, Integer> timePos = new TreeMap<>();

		public double lastLastLastPosTime;
		public double lastLastPosTime;
		int dir;
		double enterT;

		public AgentInfo(int dir, double enterT) {
			this.dir = dir;
			this.enterT = enterT;
		};

		int lastpos;
		double lastPosTime;

		@Override
		public String toString() {
			return lastpos + " " + lastPosTime;
		}
	}

	private final class Measure {

		public Measure(double time) {
			this.time = time;
		}

		double dsSpd;
		double dsRho;
		double usSpd;
		double usRho;
		double time;
		List<AgentInfo> dsAis = new ArrayList<>();
		List<AgentInfo> usAis = new ArrayList<>();
	}

	private double a1DBSplineKernel(final double r, double h) {
		final double sigma = 2d / 3d; // 1d normalization
		final double v = 1d; // 1d
		final double term1 = sigma / Math.pow(h, v);
		double q = r / h;
		if (q <= 1d) {
			final double term2 = 1d - 3d / 2d * Math.pow(q, 2d) + 3d / 4d
					* Math.pow(q, 3d);
			return term1 * term2;
		} else if (q <= 2d) {
			final double term2 = 1d / 4d * Math.pow(2d - q, 3);
			return term1 * term2;
		}
		return 0;
	}

	public void clean() {

	}

}
