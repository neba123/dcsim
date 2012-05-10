package edu.uwo.csd.dcsim2.application;

import org.apache.log4j.Logger;

import edu.uwo.csd.dcsim2.core.*;
import edu.uwo.csd.dcsim2.core.metrics.FractionalMetric;
import edu.uwo.csd.dcsim2.host.Host;
import edu.uwo.csd.dcsim2.vm.VirtualResources;

public abstract class InteractiveApplication extends Application {

	private static Logger logger = Logger.getLogger(Host.class);
	
	//variables to keep track of resource demand and consumption
	VirtualResources resourceDemand;		//the current level of resource demand / second
	VirtualResources resourceInUse;			//the current level of resource use  / second
	
	VirtualResources resourcesDemanded; 	//the amount of resources demanded in the last period
	VirtualResources resourcesUsed;			//the amount of resources used in the last period
	
	VirtualResources totalResourceDemand;	//the total amount of resources required since the application started
	VirtualResources totalResourceUsed;		//the total amount of resources used since the application started

	private double workRemaining = 0;
	private ApplicationTier applicationTier;
	private VirtualResources overhead; //the amount of overhead per second this application creates
	private VirtualResources overheadRemaining; //the amount of overhead accumulated over the elapsed period that remains to be processed
	
	private double incomingWork = 0;
	private double totalIncomingWork = 0;
	private double slaViolatedWork = 0;
	private double totalSlaViolatedWork = 0;
	private double migrationPenalty = 0;
	private double totalMigrationPenalty = 0;
	
	public InteractiveApplication(Simulation simulation, ApplicationTier applicationTier) {
		super(simulation);
		
		//initialize resource demand/consumption values
		resourceDemand = new VirtualResources();
		resourceInUse = new VirtualResources();
		resourcesDemanded = new VirtualResources();
		resourcesUsed = new VirtualResources();
		totalResourceDemand = new VirtualResources();
		totalResourceUsed = new VirtualResources();
		
		this.applicationTier = applicationTier;
		overhead = new VirtualResources(); //no overhead, by default
	}
	
	/*
	 * Called once at the beginning of scheduling
	 */
	public void prepareExecution() {
		//reset the resource demand and consumption values for the current interval
		resourcesDemanded = new VirtualResources();
		resourcesUsed = new VirtualResources();
		
		//calculate overhead for scheduling period
		overheadRemaining = new VirtualResources();
		
		overheadRemaining.setCpu(overhead.getCpu() * simulation.getElapsedSeconds());
		overheadRemaining.setBandwidth(overhead.getBandwidth() * simulation.getElapsedSeconds());
		overheadRemaining.setMemory(overhead.getMemory());
		overheadRemaining.setStorage(overhead.getStorage());
		
		//application overhead is included in resourceDemand
		resourcesDemanded = resourcesDemanded.add(overheadRemaining);
		
		//set up sla metrics
		incomingWork = 0;
		slaViolatedWork = 0;
		migrationPenalty = 0;
	}

	public void updateResourceDemand() {
		//retrieve incoming work
		double incomingWork = applicationTier.retrieveWork(this);
		workRemaining += incomingWork;
		this.incomingWork += incomingWork;
		
		//if there is incoming work, calculate the resources required to perform it and add it to resourceDemand
		if (incomingWork > 0) {
			resourcesDemanded = resourcesDemanded.add(calculateRequiredResources(incomingWork));
		}
	}
	
	public VirtualResources execute(VirtualResources resourcesAvailable) {

		VirtualResources resourcesConsumed = new VirtualResources();
		
		//first ensure that all remaining overhead for the elapsed period has been processed
		if (overheadRemaining.getCpu() > 0) {
			if (resourcesAvailable.getCpu() > overheadRemaining.getCpu()) {
				//we have enough cpu to complete processing the overhead
				resourcesAvailable.setCpu(resourcesAvailable.getCpu() - overheadRemaining.getCpu());
				resourcesConsumed.setCpu(overheadRemaining.getCpu());
				overheadRemaining.setCpu(0);
			} else {
				//we do not have enough cpu to complete processing the overhead
				overheadRemaining.setCpu(overheadRemaining.getCpu() - resourcesAvailable.getCpu());
				resourcesConsumed.setCpu(resourcesAvailable.getCpu());
				resourcesAvailable.setCpu(0);
			}
		}
		if (overheadRemaining.getBandwidth() > 0) {
			if (resourcesAvailable.getBandwidth() > overheadRemaining.getBandwidth()) {
				//we have enough bandwidth to complete processing the overhead
				resourcesAvailable.setBandwidth(resourcesAvailable.getBandwidth() - overheadRemaining.getBandwidth());
				resourcesConsumed.setBandwidth(overheadRemaining.getBandwidth());
				overheadRemaining.setBandwidth(0);
			} else {
				//we do not have enough bandwidth to complete processing the overhead
				overheadRemaining.setBandwidth(overheadRemaining.getBandwidth() - resourcesAvailable.getBandwidth());
				resourcesConsumed.setBandwidth(resourcesAvailable.getBandwidth());
				resourcesAvailable.setBandwidth(0);
			}
		}
		
		resourcesConsumed.setMemory(overheadRemaining.getMemory());
		resourcesConsumed.setStorage(overheadRemaining.getStorage());
		
		//check minimum memory and storage. If not met, assume the application does not run. TODO is this correct? Should we use what we can? How would this affect application performance?
		if (resourcesAvailable.getMemory() < overheadRemaining.getMemory() || resourcesAvailable.getStorage() < overheadRemaining.getStorage()) {
			logger.info("Application has insufficient memory or storage to meet overhead requirements");
			return new VirtualResources(); //no resources consumed
		}
		
		//next, we can process actual work
		CompletedWork completedWork = performWork(resourcesAvailable, workRemaining);
		
		if (completedWork.getWorkCompleted() > workRemaining)
			throw new RuntimeException("Application class " + this.getClass().getName() + " performed more work than was available to perform. Programming error.");
		
		applicationTier.getWorkTarget().addWork(completedWork.getWorkCompleted());
		workRemaining -= completedWork.getWorkCompleted();
	
		//compute total consumed resources
		resourcesConsumed = resourcesConsumed.add(completedWork.resourcesConsumed);
		
		//add resourcesConsumed to resourcesInUse, which is keeping track of all resources used during this time interval
		resourcesUsed = resourcesUsed.add(resourcesConsumed);
		
		return resourcesConsumed;
	}

	/*
	 * Called once at the end of scheduling
	 */
	public void completeExecution() {
		
		//convert resourceDemand and resourceInUse to a 'resource per second' value by dividing by seconds elapsed in time interval
		resourceDemand = new VirtualResources();
		resourceDemand.setCpu(resourcesDemanded.getCpu() / (simulation.getElapsedSeconds()));
		resourceDemand.setBandwidth(resourcesDemanded.getBandwidth() / (simulation.getElapsedSeconds()));
		resourceDemand.setMemory(resourcesDemanded.getMemory());
		resourceDemand.setStorage(resourcesDemanded.getStorage());
		
		resourceInUse = new VirtualResources();
		resourceInUse.setCpu(resourcesUsed.getCpu() / (simulation.getElapsedSeconds()));
		resourceInUse.setBandwidth(resourcesUsed.getBandwidth() / (simulation.getElapsedSeconds()));
		resourceInUse.setMemory(resourcesUsed.getMemory());
		resourceInUse.setStorage(resourcesUsed.getStorage());
		
		slaViolatedWork = workRemaining;
		
		
		if (vm.isMigrating()) {
			migrationPenalty += (incomingWork - workRemaining) * Double.parseDouble(simulation.getProperty("vmMigrationSLAPenalty"));
			slaViolatedWork += migrationPenalty;
		}
		
		totalIncomingWork += incomingWork;
		totalSlaViolatedWork += slaViolatedWork;
		totalMigrationPenalty += migrationPenalty;
		
		//clear work remaining (i.e. drop requests that could not be fulfilled)
		workRemaining = 0;
	}
	
	@Override
	public void updateMetrics() {
		
		//add resource demand and use for this time interval to total values
		totalResourceDemand = totalResourceDemand.add(resourcesDemanded);
		totalResourceUsed = totalResourceUsed.add(resourcesUsed);
		
		FractionalMetric.getSimulationMetric(simulation, Application.SLA_VIOLATION_METRIC).addValue(slaViolatedWork, incomingWork);
		FractionalMetric.getSimulationMetric(simulation, Application.SLA_VIOLATION_UNDERPROVISION_METRIC).addValue(slaViolatedWork - migrationPenalty, incomingWork);
		FractionalMetric.getSimulationMetric(simulation, Application.SLA_VIOLATION_MIGRATION_OVERHEAD_METRIC).addValue(migrationPenalty, incomingWork);

	}

	protected abstract VirtualResources calculateRequiredResources(double work);
	protected abstract CompletedWork performWork(VirtualResources resourcesAvailable, double workRemaining);
	
	public VirtualResources getOverhead() {
		return overhead;
	}
	
	public void setOverhead(VirtualResources overhead) {
		this.overhead = overhead;
	}
	
	@Override
	public VirtualResources getResourceDemand() {
		return resourceDemand;
	}

	@Override
	public VirtualResources getResourceInUse() {
		return resourceInUse;
	}

	@Override
	public VirtualResources getTotalResourceDemand() {
		return totalResourceDemand;
	}

	@Override
	public VirtualResources getTotalResourceUsed() {
		return totalResourceUsed;
	}
	
	@Override
	public double getSLAViolation() {
		return slaViolatedWork / incomingWork;
	}

	@Override
	public double getTotalSLAViolation() {
		return totalSlaViolatedWork / totalIncomingWork;
	}

	@Override
	public double getSLAViolatedWork() {
		return slaViolatedWork;
	}

	@Override
	public double getTotalSLAViolatedWork() {
		return totalSlaViolatedWork;
	}
	
	@Override	
	public double getMigrationPenalty() {
		return migrationPenalty;
	}
	
	@Override	
	public double getTotalMigrationPenalty() {
		return totalMigrationPenalty;
	}
	
	protected class CompletedWork {
		
		private double workCompleted;
		private VirtualResources resourcesConsumed;
		
		public CompletedWork(double workCompleted, VirtualResources resourcesConsumed) {
			this.workCompleted = workCompleted;
			this.resourcesConsumed = resourcesConsumed;
		}
		
		public double getWorkCompleted() {
			return workCompleted;
		}
		
		public VirtualResources getResourcesConsumed() {
			return resourcesConsumed;
		}
		
	}
	
}
