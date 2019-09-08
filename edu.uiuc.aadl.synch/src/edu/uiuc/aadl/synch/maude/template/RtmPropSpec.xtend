
package edu.uiuc.aadl.synch.maude.template

import edu.uiuc.aadl.xtext.propspec.propSpec.BinaryExpression
import edu.uiuc.aadl.xtext.propspec.propSpec.BinaryFormula
import edu.uiuc.aadl.xtext.propspec.propSpec.PropRef
import edu.uiuc.aadl.xtext.propspec.propSpec.StateProp
import edu.uiuc.aadl.xtext.propspec.propSpec.Top
import edu.uiuc.aadl.xtext.propspec.propSpec.UnaryExpression
import edu.uiuc.aadl.xtext.propspec.propSpec.UnaryFormula
import edu.uiuc.aadl.xtext.propspec.propSpec.Value
import edu.uiuc.aadl.xtext.propspec.propSpec.ValueProp
import org.osate.aadl2.ContainedNamedElement
import org.osate.aadl2.ContainmentPathElement
import org.osate.aadl2.PropertyValue

import static extension edu.uiuc.aadl.synch.maude.template.RtmAadlIdentifier.*
import edu.uiuc.aadl.xtext.propspec.propSpec.ReqStatement
import edu.uiuc.aadl.xtext.propspec.propSpec.Prop

class RtmPropSpec {
	
	
	static def compileTestCommand(Top top)'''
	mod TEST-«top.name.toUpperCase» is
	  including «top.name.toUpperCase»-MODEL-SYMBOLIC .
	
	  op initConst : -> BoolExp .
	  eq initConst = [true] .
	
	  op initState : -> Object .
	  eq initState = initialize(initial) .
	
	  op cinitState : -> Object .
	  eq cinitState = initialize(collapse(initial)) .
	
	
	  eq @m@ = ['«top.name.toUpperCase»-MODEL-SYMBOLIC] .
	endm
	
	red initState .
	
	search [,«top.bound»] {lookup(initState, «(top.initCond as Prop).path.compilePath», «(top.initCond as ValueProp).expression.compileExp»)  || initState, false} =>+ 
		{B:BoolExp || OBJ:Object, false} such that check-sat(B:BoolExp and lookup(OBJ:Object, «(top.finCond as Prop).path.compilePath», «(top.initCond as ValueProp).expression.compileExp») .
	'''
	
	static def compileSearchCommand(Top top)'''
	mod TEST-«top.name.toUpperCase» is
		including «top.name.toUpperCase»-MODEL .
		including MODEL-TRANSITION-SYSTEM .
		
		op initConst : -> BoolExp .
		eq initConst = «top.initCond» .
		
		op finalConst : -> BoolExp .
		eq finalConst = «top.finCond» .
		
		op initState : -> Object .
		eq initState = initialize(initial) .
		
		var OBJ : Object .
		var CONST : BoolExp .
	endm
	
	search [,«top.bound»] {initConst || initState, true} =>+ { CONST || OBJ, true } 
	 					   such that check-sat(CONST and finalonst) .
	'''
	
	static def compileSpec(Top top) '''
		load «top.name».maude .
		load «RtmAadlSetting::SEMANTICS_PATH»/«RtmAadlSetting::ANALYSIS_FILE» .
		
		fmod «top.name.toUpperCase»-VERIFICATION-DEF is
			including «top.name.toUpperCase»-MODEL .
			including AADL-SIMPLE-COUNTEREXAMPLE .
			
			--- requirements
			«FOR r : top.requirements»
			op «r.name» : -> Formula .	«IF r.bound > 0»--- bound = «r.bound»«ENDIF»
			eq «r.name»
			 = «r.value.compileFormula» .
			
			«ENDFOR»
			--- formulas and propositions
			«FOR f : top.formulas»
			op «f.name» : -> Formula .
			eq «f.name»
			 = «f.value.compileFormula» .
			
			«ENDFOR»
		endm
	'''
	
	static def compileReqCommand(ReqStatement req) {
		if (req.bound > 0)
			'''(mc {initial} |=t «req.name.escape» in time <= «req.bound» .)'''
		else
			'''(mc {initial} |=u «req.name.escape» .)'''
	}
	
	static def compileSimulCommand(int bound) '''
		(trew {initial} in time <= «bound»  .)
	'''
	
	/**
	 *  translate LTL formulas
	 */
	 
	 static def compileRewCommand(int bound) '''
	 	rew {initial} .
	 '''
	 
	private static def dispatch CharSequence compileFormula(BinaryFormula f) 
	'''(«f.left.compileFormula» «f.op» «f.right.compileFormula»)'''
			
	
	private static def dispatch CharSequence compileFormula(UnaryFormula f) {
		if (f.child == null) f.op else '''(«f.op» «f.child.compileFormula»)'''
	}
			
	private static def dispatch CharSequence compileFormula(PropRef pr) 
	'''«pr.def.name»'''
	
	
	private static def dispatch CharSequence compileFormula(StateProp prop) 
	'''«prop.path.compilePath» @ «prop.state»'''
	
	
	private static def dispatch CharSequence compileFormula(ValueProp prop) 
	'''«prop.path.compilePath» | «prop.expression.compileExp»'''
	 
	  
	/**
	 *  translate BA expressions
	 */
	private static def dispatch CharSequence compileExp(BinaryExpression e) '''
		(«e.left.compileExp» «e.op» «e.right.compileExp»)'''
		
		
	private static def dispatch CharSequence compileExp(UnaryExpression e) '''
		«e.op.translateUnaryOp»(«e.child.compileExp»)'''
	
	
	private static def dispatch CharSequence compileExp(Value e) {
		val v = e.value
		switch v {
			PropertyValue:			'''[«RtmAadlProperty::compilePropertyValue(v)»]'''
			ContainmentPathElement:	'''c[«v.namedElement.name.escape»]'''
		}
	}
	
	private static def translateUnaryOp(String op) {
		switch op {
			case "+":	"plus"
			case "-":	"minus"
			default:	op
		}
	}
	
	// an component path
	private static def CharSequence compilePath(ContainedNamedElement path) {
		path.containmentPathElements.map[namedElement.name.escape].join(' . ')
	}
}