package edu.postech.aadl.xtext.propspec.linking;
import java.util.Collections;
import java.util.List;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.xtext.linking.impl.DefaultLinkingService;
import org.eclipse.xtext.linking.impl.IllegalNodeException;
import org.eclipse.xtext.nodemodel.INode;
import org.osate.aadl2.Aadl2Package;
import org.osate.aadl2.AbstractSubcomponent;
import org.osate.aadl2.BehavioredImplementation;
import org.osate.aadl2.ComponentClassifier;
import org.osate.aadl2.ComponentPrototype;
import org.osate.aadl2.ContainedNamedElement;
import org.osate.aadl2.ContainmentPathElement;
import org.osate.aadl2.FeatureGroup;
import org.osate.aadl2.FeatureGroupPrototype;
import org.osate.aadl2.FeatureGroupType;
import org.osate.aadl2.NamedElement;
import org.osate.aadl2.PropertyExpression;
import org.osate.aadl2.Subcomponent;
import org.osate.aadl2.SubprogramSubcomponent;
import org.osate.aadl2.ThreadSubcomponent;
import org.osate.aadl2.modelsupport.util.AadlUtil;
import org.osate.aadl2.modelsupport.util.ResolvePrototypeUtil;

import edu.postech.aadl.xtext.propspec.propSpec.FormulaStatement;
import edu.postech.aadl.xtext.propspec.propSpec.Prop;
import edu.postech.aadl.xtext.propspec.propSpec.Top;
import edu.postech.aadl.xtext.propspec.propSpec.ValueProp;

public class PropSpecLinkingService extends DefaultLinkingService {

	/**
	 * returns the first linked object
	 */
	@Override
	public List<EObject> getLinkedObjects(EObject context, EReference reference, INode node)
			throws IllegalNodeException {
		final EClass requiredType = reference.getEReferenceType();
		if (requiredType == null) {
			return Collections.<EObject> emptyList();
		}

		final String crossRefString = getCrossRefNodeAsString(node);


		if (Aadl2Package.eINSTANCE.getNamedElement() == requiredType) {
			if (context instanceof ContainmentPathElement) {
				EObject res = null;

				if (context.eContainer() instanceof ContainmentPathElement) // inside a path
				{
					ContainmentPathElement el = (ContainmentPathElement) ((ContainmentPathElement) context).getOwner();
					res = findNamedObject(el, crossRefString);
				} else if (context.eContainer() instanceof PropertyExpression) // inside an expression
				{
					ContainmentPathElement pl = getPropPathElement(context);
					System.out.println("ContainmentPathElement : " + pl);
					System.out.println("CrossRefString : " + crossRefString);
					if (pl != null) {
						res = findNamedObject(pl, crossRefString);
						System.out.println("Resource : " + res);
					}
				}
				else {
					if (context.eContainer().eContainer().eContainer() instanceof FormulaStatement) {
						ComponentClassifier ns = getContainingModelClassifier(context);
						if (ns != null) {
							res = ns.findNamedElement(crossRefString);
						}
					} else if (context.eContainer().eContainer().eContainer() instanceof ValueProp) {
						ContainedNamedElement path = ((ValueProp)context.eContainer().eContainer().eContainer()).getPath();
						List<ContainmentPathElement> list = path.getContainmentPathElements();
						ContainmentPathElement el = list.get(list.size() - 1);
						res = findNamedObject(el, crossRefString);
					}
				}
				if (res != null && res instanceof NamedElement) {
					return Collections.singletonList(res);
				}
			}
		}

		return super.getLinkedObjects(context, reference, node);
	}

	private static ComponentClassifier getContainingModelClassifier(EObject element) {
		EObject container = element;
		while (container != null) {
			if (container instanceof Top) {
				return ((Top) container).getModel();
			}
			container = container.eContainer();
		}
		return null;
	}

	private static ContainmentPathElement getPropPathElement(EObject element) {
		EObject container = element;
		while (container != null) {
			if (container instanceof Prop)
			{
				ContainedNamedElement path = ((Prop) container).getPath();
				List<ContainmentPathElement> list = path.getContainmentPathElements();
				return list.get(list.size() - 1);
			}
			container = container.eContainer();
		}
		return null;
	}

	// find an element in namespace of a given ContainmentPathElement.
	private EObject findNamedObject(ContainmentPathElement el, String crossRefString) {
		EObject res = null;
		NamedElement ne = el.getNamedElement();

		if (ne instanceof Subcomponent) {
			Subcomponent subcomponent = (Subcomponent) ne;
			while (subcomponent.getSubcomponentType() == null && subcomponent.getRefined() != null) {
				subcomponent = subcomponent.getRefined();
			}
			ComponentClassifier ns = null;
			if (subcomponent.getSubcomponentType() instanceof ComponentClassifier) {
				ns = (ComponentClassifier) subcomponent.getSubcomponentType();
			} else if (subcomponent.getSubcomponentType() instanceof ComponentPrototype) {
				ns = ResolvePrototypeUtil
						.resolveComponentPrototype((ComponentPrototype) subcomponent.getSubcomponentType(), el);
			}
			if (ns != null) {
				res = ns.findNamedElement(crossRefString);
				if (res == null && (ne instanceof ThreadSubcomponent || ne instanceof SubprogramSubcomponent
						|| ne instanceof AbstractSubcomponent) && ns instanceof BehavioredImplementation) {
					res = AadlUtil.findNamedElementInList(((BehavioredImplementation) ns).subprogramCalls(),
							crossRefString);
				}
			}
		} else if (ne instanceof FeatureGroup) {
			FeatureGroup featureGroup = (FeatureGroup) ne;
			while (featureGroup.getFeatureType() == null && featureGroup.getRefined() instanceof FeatureGroup) {
				featureGroup = (FeatureGroup) featureGroup.getRefined();
			}
			FeatureGroupType ns = null;
			if (featureGroup.getFeatureType() instanceof FeatureGroupType) {
				ns = (FeatureGroupType) featureGroup.getFeatureType();
			} else if (featureGroup.getFeatureType() instanceof FeatureGroupPrototype) {
				ns = ResolvePrototypeUtil
						.resolveFeatureGroupPrototype((FeatureGroupPrototype) featureGroup.getFeatureType(), el);
			}
			if (ns != null) {
				res = ns.findNamedElement(crossRefString);
			}
		}
		return res;
	}
}
