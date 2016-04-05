package compiler

import org.scalatest.FunSuite

class compilerTestSuite extends FunSuite {
  
  def files = new {
    
    val sampleVSet1 = "test/compiler/sampleVSet1.txt"
    
  }
  
  
  test("VSetAReader should correctly acquire sampleVSet1") {
    
    val (nrStates, initial, transitionFunction, finalStates) = VSetAFileReader.getVSetA(files.sampleVSet1)
    
    assert(transitionFunction(0)(1) == "x_in")
    assert(transitionFunction(0)(4) == "y_in")
    assert(transitionFunction(1)(2) == "a")
    assert(transitionFunction(2)(3) == "x_out")
    assert(transitionFunction(4)(5) == "b")
    assert(transitionFunction(5)(3) == "y_out")
    
    assert(transitionFunction(0).size == 2)
    assert(transitionFunction(1).size == 1)
    assert(transitionFunction(2).size == 1)
    assert(transitionFunction(4).size == 1)
    assert(transitionFunction(5).size == 1)
    
    assert(finalStates(0) == 3)
    
    assert(finalStates.size == 1)
    
    assert(nrStates == 6)
    
    assert(initial == 0)
    
  }
  
  test("stateElimination() should correctly eliminate the states of sampleVSet1") {
    
    val (nrStates, initial, transitionFunction, finalStates) = VSetAFileReader.getVSetA(files.sampleVSet1)
    
    val a = new VSetAutomaton(nrStates, initial, transitionFunction, finalStates)
    
    val (nrStates2, initial2, newTransitionGraph, finalStates2) = a.stateElimination()
    
    assert(transitionFunction(0)(1) == "x_in")
    assert(transitionFunction(0)(4) == "y_in")
    assert(transitionFunction(1)(3) == "((a)(x_out))")
    assert(transitionFunction(4)(3) == "((b)(y_out))")
    
  }
  
  test("toVSetPathUnion() should correctly sampleVSet1 into a vset path union") {
    
    val (nrStates, initial, transitionFunction, finalStates) = VSetAFileReader.getVSetA(files.sampleVSet1)
    
    val a = new VSetAutomaton(nrStates, initial, transitionFunction, finalStates)
    
    val (transitionGraph, finalStates2) = a.toVSetPathUnion()
    
  }
  
}