package edu.uwo.csd.dcsim2.application;

import java.util.ArrayList;

import edu.uwo.csd.dcsim2.core.Simulation;
import edu.uwo.csd.dcsim2.core.Utility;
import edu.uwo.csd.dcsim2.vm.*;

public class VmmApplication implements Application {

	private ArrayList<VM> migratingVms = new ArrayList<VM>();
	private VirtualResources resourcesRemaining;
	
	public void addMigratingVm(VM vm) {
		migratingVms.add(vm);
	}
	
	public void removeMigratingVm(VM vm) {
		migratingVms.remove(vm);
	}
	
	@Override
	public void beginScheduling() {
		long elapsedTime = Simulation.getSimulation().getElapsedTime();
		
		double cpu = 0;
		double bandwidth = 0;
		for (VM migrating : migratingVms) {
			
			/*  Calculate CPU overhead as 10% of utilization over last 
			 *  elapsed period for each migrating VM. Note, it may be
			 *  more accurate to use 10% of current utilization, but this
			 *  is not possible due to the way VM CPU is scheduled in the
			 *  simulator. It is unlikely to have changed drastically since
			 *  the last elapsed period, so this should be sufficiently accurate
			 *  (especially given that 10% is merely an estimation in any case).
			 */
			cpu += migrating.getResourcesInUse().getCpu() * 0.1;
			Utility.roundDouble(cpu); //round off double precision problems
			
			bandwidth += 100; //TODO: add proper overhead calculation
		}
		//TODO calculate memory and storage usage
		
		resourcesRemaining = new VirtualResources();
		resourcesRemaining.setCpu(cpu * (elapsedTime / 1000d));
		resourcesRemaining.setBandwidth(bandwidth * (elapsedTime / 1000d));
	}

	@Override
	public void completeScheduling() {
		//at this point, no resources should be remaining to run
		if (resourcesRemaining.getCpu() != 0 || resourcesRemaining.getBandwidth() != 0) {
			throw new RuntimeException(Simulation.getSimulation().getSimulationTime() + " - VMM was underallocated");
		}
	}

	@Override
	public VirtualResources runApplication(VirtualResources resourcesAvailable) {
		VirtualResources resourcesConsumed = new VirtualResources();
		
		//consume cpu
		if (resourcesAvailable.getCpu() >= resourcesRemaining.getCpu()) {
			resourcesConsumed.setCpu(resourcesRemaining.getCpu());
			resourcesRemaining.setCpu(0);
		} else {
			resourcesConsumed.setCpu(resourcesAvailable.getCpu());
			resourcesRemaining.setCpu(resourcesRemaining.getCpu() - resourcesAvailable.getCpu());
		}

		//consume bandwidth
		if (resourcesAvailable.getBandwidth() >= resourcesRemaining.getBandwidth()) {
			resourcesConsumed.setBandwidth(resourcesRemaining.getBandwidth());
			resourcesRemaining.setBandwidth(0);
		} else {
			resourcesConsumed.setBandwidth(resourcesAvailable.getBandwidth());
			resourcesRemaining.setBandwidth(resourcesRemaining.getBandwidth() - resourcesAvailable.getBandwidth());
		}
		
		//TODO handle memory and storage
		
		return resourcesConsumed;
	}

	@Override
	public double getResourcesRequired() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public double getResourcesInUse() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public double getTotalResourcesRequired() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public double getTotalResourcesConsumed() {
		// TODO Auto-generated method stub
		return 0;
	}

}
