package edu.uwo.csd.dcsim2.application;

import edu.uwo.csd.dcsim2.core.Simulation;

public class VmmApplicationFactory implements ApplicationFactory {

	@Override
	public Application createApplication(Simulation simulation) {
		return new VmmApplication(simulation);
	}

	@Override
	public int getHeight() {
		return Integer.MAX_VALUE;
	}

	@Override
	public void startApplication(Application application) {
		//nothing to do
	}

	@Override
	public void stopApplication(Application application) {
		//nothing to do
	}

}
