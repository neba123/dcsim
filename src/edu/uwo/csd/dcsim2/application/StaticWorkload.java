package edu.uwo.csd.dcsim2.application;

import edu.uwo.csd.dcsim2.core.Simulation;

public class StaticWorkload extends Workload {

	int workPerSecond;
	
	public StaticWorkload(int workPerSecond) {
		super();
		
		this.workPerSecond = workPerSecond;
	}

	@Override
	protected double retrievePendingWork() {
		return workPerSecond * ((Simulation.getSimulation().getSimulationTime() - Simulation.getSimulation().getLastUpdate()) / 1000.0);
	}

	@Override
	protected long updateWorkLevel() {
		//do nothing, static workload never changes
		return 0;
	}

}
