package edu.postech.aadl.synch.checker;

import org.eclipse.core.runtime.IProgressMonitor;
import org.osate.aadl2.ComponentCategory;
import org.osate.aadl2.instance.ComponentInstance;
import org.osate.aadl2.instance.ConnectionInstance;
import org.osate.aadl2.instance.FeatureInstance;
import org.osate.aadl2.instance.util.InstanceSwitch;
import org.osate.aadl2.modelsupport.errorreporting.AnalysisErrorReporterManager;
import org.osate.aadl2.modelsupport.modeltraversal.AadlProcessingSwitchWithProgress;
import org.osate.aadl2.util.Aadl2InstanceUtil;
import org.osate.xtext.aadl2.properties.util.GetProperties;

import edu.postech.aadl.synch.maude.contspec.ContSpec;
import edu.postech.aadl.utils.PropertyUtil;

public class SynchAadlConstChecker extends AadlProcessingSwitchWithProgress {

	public SynchAadlConstChecker(IProgressMonitor pm, AnalysisErrorReporterManager errMgr) {
		super(pm, errMgr);
	}

	@Override
	protected void initSwitches() {
		instanceSwitch = new InstanceSwitch<String>() {

			@Override
			public String caseComponentInstance(ComponentInstance ci) {
				checkCompSynch(ci);
				checkCompPeriod(ci);

				checkFeatDataOutInitValue(ci);
				checkFeatDataOutParamValue(ci);

				checkEnvCompDataType(ci);
				checkEnvSubCompType(ci);
				checkEnvCompHasCD(ci);
				checkNonEnvCompHasCD(ci);

				checkEnvFlowsDirectReferPort(ci);
				checkEnvFlowsWrongParam(ci);

				checkCompSubDataInitValue(ci);
				checkCompSampleTime(ci);
				checkCompResponseTime(ci);
				checkCompMaxClockDev(ci);

				monitor.worked(1);
				return DONE;
			}

			@Override
			public String caseFeatureInstance(FeatureInstance fi) {
				checkOutPortConnMissing(fi);
				// checkInPortConnMissing(fi);
				monitor.worked(1);
				return DONE;
			}

			@Override
			public String caseConnectionInstance(ConnectionInstance ci) {
				checkConnDelayedComp(ci);
				checkConnBetweenEnvComps(ci);
				checkConnEnvOutPortType(ci);

				monitor.worked(1);
				return DONE;
			}
		};
	}

	private void checkCompSynch(ComponentInstance ci) {
		if (ci.getCategory() != ComponentCategory.DATA && !PropertyUtil.isEnvironment(ci)
				&& !PropertyUtil.isSynchronous(ci)) {
				getErrorManager().error(ci, ci.getName() + " is not a synchronous " + ci.getCategory().getLiteral());
		}
	}

	private void checkCompPeriod(ComponentInstance ci) {
		if (ci.getCategory() != ComponentCategory.DATA) {
			double period = GetProperties.getPeriodinMS(ci);
			ComponentInstance parent = ci.getContainingComponentInstance();
			if (parent != null) {
				double parentPeriod = GetProperties.getPeriodinMS(parent);
				if (parentPeriod != period) {
					getErrorManager().error(ci, ci.getName() + " have different period with others");
				}
			}
		}
	}

	private void checkFeatDataOutInitValue(ComponentInstance ci) {
		if (PropertyUtil.isTopComponent(ci)) {
			for (ComponentInstance sub : ci.getComponentInstances()) {
				if (!PropertyUtil.isEnvironment(sub)) {
					for (FeatureInstance fi : sub.getFeatureInstances()) {
						if (fi.getDirection().outgoing() && PropertyUtil.isDataPortFeature(fi)
								&& PropertyUtil.getSynchListProp(fi,
								PropertyUtil.DATA_MODEL,
								PropertyUtil.INITIAL_VALUE, null) == null) {
							getErrorManager().error(fi,
									fi.getName() + " does not have a Data_Model::Initial_Value property");
						}
					}
				}
			}
		}
	}

	private void checkFeatDataOutParamValue(ComponentInstance ci) {
		if (PropertyUtil.isTopComponent(ci)) {
			for (ComponentInstance sub : ci.getComponentInstances()) {
				if (!PropertyUtil.isEnvironment(sub)) {
					for (FeatureInstance fi : sub.getFeatureInstances()) {
						if (fi.getDirection().outgoing() && PropertyUtil.isParameterized(fi)) {
							getErrorManager().error(fi, fi.getName() + " is not allowed to use parameterized value");
						}
					}
				}
			}
		}
	}

	private void checkEnvCompDataType(ComponentInstance ci) {
		if (PropertyUtil.isEnvironment(ci)) {
			for (ComponentInstance sub : ci.getComponentInstances()) {
				if (sub.getCategory() == ComponentCategory.DATA && !sub.getClassifier().getName().equals("Float")) {
					getErrorManager().error(ci, ci.getName() + " must be float type data component");
				}
			}
		}
	}

	private void checkEnvSubCompType(ComponentInstance ci) {
		if (PropertyUtil.isEnvironment(ci)) {
			for (ComponentInstance sub : ci.getComponentInstances()) {
				if (sub.getCategory() != ComponentCategory.DATA) {
					getErrorManager().error(ci, ci.getName() + " can not have subcomponent without data component");
				}
			}
		}
	}

	private void checkEnvCompHasCD(ComponentInstance ci) {
		if (PropertyUtil.isEnvironment(ci) && PropertyUtil.getSynchStringProp(ci, PropertyUtil.HYBRIDSYNCHAADL,
				PropertyUtil.CONTINUOUSDYNAMIC, null) == null) {
			getErrorManager().error(ci, ci.getName() + " must have Continuous Dynamics property");
		}
	}

	private void checkNonEnvCompHasCD(ComponentInstance ci) {
		if (!PropertyUtil.isEnvironment(ci) && PropertyUtil.getSynchStringProp(ci, PropertyUtil.HYBRIDSYNCHAADL,
				PropertyUtil.CONTINUOUSDYNAMIC, null) != null) {
			getErrorManager().error(ci, ci.getName() + " must not have Continuous Dynamics property");
		}
	}

	private void checkEnvFlowsDirectReferPort(ComponentInstance ci) {
		String cont = PropertyUtil.getSynchStringProp(ci, PropertyUtil.HYBRIDSYNCHAADL, PropertyUtil.CONTINUOUSDYNAMIC,
				null);
		if (cont != null) {
			ContSpec spec = ContSpec.parse(cont, ci);
			for (FeatureInstance fi : ci.getFeatureInstances()) {
				if (spec.findValueVariable(fi.getFeature().getName())) {
						getErrorManager().error(ci,
								ci.getName() + " should have continuous dynamics without directly using port value");
					}
			}
		}
	}

	private void checkEnvFlowsWrongParam(ComponentInstance ci) {
		String cont = PropertyUtil.getSynchStringProp(ci, PropertyUtil.HYBRIDSYNCHAADL, PropertyUtil.CONTINUOUSDYNAMIC,
				null);
		if (cont != null) {
			ContSpec spec = ContSpec.parse(cont, ci);
			if (!spec.isValidParameter()) {
				getErrorManager().error(ci, ci.getName() + " has wrong parameter value in continuous dynamics");
			}
		}
	}

	private void checkCompSubDataInitValue(ComponentInstance ci) {
		if (ci.getCategory() == ComponentCategory.DATA && PropertyUtil.getSynchListProp(ci,
				PropertyUtil.DATA_MODEL, PropertyUtil.INITIAL_VALUE, null) == null) {
			getErrorManager().error(ci, ci.getName() + " does not have a Data_Model::Initial_Value property");
		}
	}

	private void checkCompSampleTime(ComponentInstance ci) {
		if ((ci.getCategory() == ComponentCategory.SYSTEM || ci.getCategory() == ComponentCategory.THREAD)
				&& !PropertyUtil.isTopComponent(ci)
				&& PropertyUtil.getSynchRangeProp(ci, PropertyUtil.HYBRIDSYNCHAADL, PropertyUtil.SAMPLING_TIME,
						null) == null) {
			getErrorManager().error(ci, ci.getName() + " does not have a Hybrid_SynchAADL::Sampling_Time");
		}
	}

	private void checkCompResponseTime(ComponentInstance ci) {
		if ((ci.getCategory() == ComponentCategory.SYSTEM || ci.getCategory() == ComponentCategory.THREAD)
				&& !PropertyUtil.isTopComponent(ci)
				&& PropertyUtil.getSynchRangeProp(ci, PropertyUtil.HYBRIDSYNCHAADL, PropertyUtil.RESPONSE_TIME,
						null) == null) {
			getErrorManager().error(ci, ci.getName() + " does not have a Hybrid_SynchAADL::Response_Time");
		}
	}

	private void checkCompMaxClockDev(ComponentInstance ci) {
		if ((ci.getCategory() == ComponentCategory.SYSTEM)
				&& PropertyUtil.getSynchIntegerProp(ci, PropertyUtil.HYBRIDSYNCHAADL, PropertyUtil.MAX_CLOCK_DEV,
						0) == 0) {
			getErrorManager().error(ci, ci.getName() + " does not have a Hybrid_SynchAADL::Max_Clock_Deviation");
		}
	}

	private void checkOutPortConnMissing(FeatureInstance fi) {
		if (fi.getDirection().outgoing()
				&& Aadl2InstanceUtil.getOutgoingConnection(fi.getComponentInstance(), fi).size() == 0) {
			getErrorManager().error(fi, fi.getName() + " is not connected");
		}
	}

	private void checkInPortConnMissing(FeatureInstance fi) {
		if (fi.getDirection().incoming()
				&& Aadl2InstanceUtil
						.getIncomingConnection(fi.getComponentInstance(), fi)
						.size() == 0) {
			getErrorManager().error(fi, fi.getName() + " is not connected");
		}
	}

	private void checkConnDelayedComp(ConnectionInstance ci) {
		ComponentInstance src = PropertyUtil.getSrcComponent(ci);
		ComponentInstance dst = PropertyUtil.getDstComponent(ci);
		if (!PropertyUtil.isEnvironment(dst) && !PropertyUtil.isEnvironment(src)
				&& !PropertyUtil.isDelayedPortConnection(ci)) {
			getErrorManager().error(ci, ci.getName() + " should be delayed connection");
		}
	}

	private void checkConnBetweenEnvComps(ConnectionInstance ci) {
		ComponentInstance src = PropertyUtil.getSrcComponent(ci);
		ComponentInstance dst = PropertyUtil.getDstComponent(ci);
		if (PropertyUtil.isEnvironment(src) && PropertyUtil.isEnvironment(dst)) {
			getErrorManager().error(ci, ci.getName() + " is not allowed connection");
		}
	}

	private void checkConnEnvOutPortType(ConnectionInstance ci) {
		ComponentInstance src = PropertyUtil.getSrcComponent(ci);
		ComponentInstance dst = PropertyUtil.getDstComponent(ci);
		if (PropertyUtil.isEnvironment(src) && !PropertyUtil.isEnvironment(dst)
				&& !PropertyUtil.isDataPortConnection(ci)) {
			getErrorManager().error(ci, ci.getName() + " should be data connection");
		}
	}



}