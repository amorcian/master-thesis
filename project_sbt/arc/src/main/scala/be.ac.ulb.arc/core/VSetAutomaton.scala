package be.ac.ulb.arc.core

import scala.collection.immutable.{HashSet => SVars}
import scala.collection.immutable.{HashSet => SVOps}
import scala.collection.immutable.{HashSet => TransitionFunction}
import scala.collection.immutable.{HashSet => StateSet}
import scala.collection.immutable.{HashSet => Predecessors}
import scala.collection.mutable.{ArrayBuffer => Program, Map}
import scala.{Int => SVar}

import be.ac.ulb.arc.runtime._

/**
  * Represents a variable-set automaton.
  * @param Q
  * @param q0
  * @param qf
  * @param δ
  */
class VSetAutomaton(val Q:StateSet[State], val q0:State, val qf:State, val V:SVars[SVar], val δ:TransitionFunction[Transition[State]]) {

  type State2= (State, State)

  /**
    * Translates the vset-atomaton into a NFA program.
    * @param program
    * @param visitedStates
    * @param inQ
    * @param pc
    * @return
    */
  def toNFAProgram(program:Program[Instruction], visitedStates:Map[State, Int], inQ:State, pc:Int): Int = {

    var npc = pc

    visitedStates+= ((inQ, npc))

    // A final state translates into a match operation, and it
    // doesn't go through the previous block because it doesn't have
    // outgoing transitions
    if(inQ == qf) {

      program += new MATCH(npc)
      npc += 1
    }
    else {

      var j = 0
      var oldSplit:SPLIT = null

      val tr = this.δ.filter((t:Transition[State]) => t.q == inQ)

      for(t <- tr) {

        var instr:Instruction = null
        var firstInstr:Instruction = null
        var jmp:JUMP = null
        var split:SPLIT = null

        // If there are >1 outgoing transitions for the current state, we need to put
        // splits
        if(tr.size > 1 && j < tr.size - 1) {

          split = new SPLIT(npc, -1, -1)
          program += split
          npc += 1

          // If it is not the first split, connect the previous to this
          if(oldSplit != null) {

            oldSplit.next2 = split.pos
          }
        }

        var instAdded = true
        // Find out the type of transition and add corresponding instruction to the program
        t match {

          case OrdinaryTransition(q:State, σ:Char, v:SVars[SVar], q1:State) => {

            instr = new CHAR(σ, npc)
            program += instr
            npc +=1
          }
          case RangeTransition(q:State, σ:Range, v:SVars[SVar], q1:State) => {

            instr = new RANGE(σ, npc)
            program += instr
            npc +=1
          }
          case OperationsTransition(q:State, s:SVOps[SVOp], v:SVars[SVar], q1:State) => {

            val cpc = npc

            for(o <- s) {

              val pos = if (o.t == ⊢) 0 else 1
              program += new SAVE(2*o.x + pos, npc)
              npc +=1

            }
            instr = program.last

            if(s.size == 0)
              instAdded = false
            else firstInstr = program(cpc)
          }

        }

        // if we placed a split, connect it to the first instruction
        if(split != null) {
          split.next1 = if(!instAdded) instr.pos + 1 else if(firstInstr != null) firstInstr.pos else instr.pos
          oldSplit = split
        }
        else if(oldSplit != null) {

          oldSplit.next2 = if(!instAdded) instr.pos + 1 else if(firstInstr != null) firstInstr.pos else instr.pos
        }

        if(visitedStates.contains(t.q1)) {

          program += new JUMP(npc, visitedStates(t.q1))
          npc += 1
        }
        else {

          // Recur
          val n1pc = toNFAProgram(program, visitedStates, t.q1, npc)
          npc = n1pc

        }

        j += 1
      }
    }

    npc
  }

  /**
    * Returns a string representation of the automaton. WARNING: For debug purposes only!
    * @return
    */
  override def toString():String = {

    var stateMap = Map[State, Int]()
    var counter = 2
    stateMap += ((q0, 0))
    stateMap += ((qf, 1))

    var s = ""
    s += Q.size.toString + '\n'
    s += "0" + '\n'
    s += "1" + '\n'
    for(v <- V)
      s += v.toString  + " "
    s += '\n'
    s += "-" + '\n'
    for(t <- δ) {

      var sQ = ""
      var dQ = ""

      if(stateMap.contains(t.q))
        sQ += stateMap(t.q)
      else {
        stateMap += ((t.q, counter))
        sQ += counter
        counter += 1
      }

      if(stateMap.contains(t.q1))
        dQ += stateMap(t.q1)
      else {
        stateMap += ((t.q1, counter))
        dQ += counter
        counter += 1
      }

      val label:String = t match {

        case OrdinaryTransition(q, σ, v, q1) => {
          σ.toString
        }
        case RangeTransition(q, σ, v, q1) => {
          "(" + σ.min.toChar + ", " + σ.max.toChar + ")"
        }
        case OperationsTransition(q, s, v, q1) => {
          var l = "{"

          for(o <- s) {
            l += o.x + (if(o.t == ⊢) "⊢" else "⊣") + ", "
          }

          l += "}"

          l
        }
      }

      s += sQ + " " + label + " " + dQ + '\n'
    }

    s
  }

  /**
    * Performs the union of this vset-automaton with another one.
    * @param other
    * @return
    */
  def ∪(other: VSetAutomaton): Option[VSetAutomaton] = {

    // If the automata are not variable compatible, abort
    if (this.V != other.V) return None

    // Get the union of the input state sets
    var newQ = this.Q ++ other.Q
    // Get the union of the input transition functions
    var newδ = this.δ ++ other.δ
    // Create the new final and initial states
    var newQ0 = new State
    var newQf = new State
    newQ = newQ + newQ0 + newQf

    // Connect the original and initial final states to the new ones
    newδ = newδ + new OperationsTransition[State](newQ0, new SVOps, this.V, this.q0)
    newδ = newδ + new OperationsTransition[State](newQ0, new SVOps, this.V, other.q0)
    newδ = newδ + new OperationsTransition[State](this.qf, new SVOps, this.V, newQf)
    newδ = newδ + new OperationsTransition[State](other.qf, new SVOps, this.V, newQf)

    // Eliminate loops on old initial and final states
    newδ = newδ.diff(newδ.filter((t:Transition[State]) => (t.q == this.qf && t.q1 == this.qf) || (t.q == other.qf && t.q1 == other.qf)))
    newδ = newδ.diff(newδ.filter((t:Transition[State]) => (t.q == this.q0 && t.q1 == this.q0) || (t.q == other.q0 && t.q1 == other.q0)))

    //Unanchored matching
    newδ = newδ + new RangeTransition[State](newQ0, new Range(Char.MinValue, Char.MaxValue), this.V, newQ0)
    newδ = newδ + new RangeTransition[State](newQf, new Range(Char.MinValue, Char.MaxValue), this.V, newQf)

    Some(new VSetAutomaton(newQ, newQ0, newQf, this.V, newδ))
  }

  /**
    * Performs the projection of this vset-automaton on the desired span variables.
    * @param vars
    * @return
    */
  def π(vars:SVars[SVar]): VSetAutomaton = {

    val nvars = this.V.diff(vars)

    // Get the transitions involving variable operations
    val tOps = this.δ.filter((t:Transition[State]) => t.isInstanceOf[OperationsTransition[State]])
    // The other transitions
    val tOther = this.δ.diff(tOps)

    // Include the other transitions as they are
    var newδ = tOther

    // Eliminate the variable operations involving variables not in vars in each operations
    // transition, add it to the new δ
    for(tO <- tOps) {

      val t = tO.asInstanceOf[OperationsTransition[State]]
      var newS = t.S

      for (op <- t.S) {

        if(nvars.contains(op.x))
          newS = newS - op
      }

      newδ = newδ + new OperationsTransition[State](t.q, newS, t.V, t.q1)
    }

    new VSetAutomaton(this.Q, this.q0, this.qf, vars, newδ)
  }

  /**
    * Returns the join of this vset-automaton with another one.
    * @param other
    * @return
    */
  def ⋈(other: VSetAutomaton): Option[VSetAutomaton] = {

    val q02:State2 = (this.q0, other.q0)
    val qf2:State2 = (this.qf, other.qf)

    var t:VSetAutomaton = null
    var o:VSetAutomaton = null
    var closed = false

    // If the automata have common variables,
    // do the closure as preprocessing
    // remove redundant states and transitions
    if(this.V.intersect(other.V).size != 0) {

      t = this.ε
      t = t.removeStates(t.hasOnlyOperationsTransitions)
      o = other.ε
      o = o.removeStates(o.hasOnlyOperationsTransitions)
      closed = true
    }
    else {

      t = this
      o = other
    }

    // Perform Cross Product
    val (intδ, intQ) = t × o

    if(!intQ.contains(q02) || !intQ.contains(qf2))
      return None

    // Prune away invalid states
    val (prδ, prQ) = prune[State2](intδ, intQ, q02, qf2)

    // Convert state pairs into simple states
    val (newQ, q0Opt, qfOpt, newδ) = State2toState(prQ, q02, qf2, prδ)

    if(q0Opt == None || qfOpt == None)
      return None

    val q0 = q0Opt.get
    val qf = qfOpt.get

    val result = new VSetAutomaton(newQ, q0, qf, t.V.union(o.V), newδ)

    if(closed) {
      val reduced = result.removeStates(result.hasOnlyOperationsTransitions)
      val  (prδ, prQ) = reduced.prune[State](reduced.δ, reduced.Q, reduced.q0, reduced.qf)
      Some(new VSetAutomaton(prQ, reduced.q0, reduced.qf, reduced.V, prδ))
    }
    else Some(result)
  }

  /**
    * Returns a vset-automaton that is the ε-closure of this one.
    * @return
    */
  def ε(): VSetAutomaton = {

    var newδ = this.δ

    // A frontier is the set of states being examined. Each state has a
    // set of predecessors, by which it is reached directly with an
    // operations transition
    var cF = Map[State, Predecessors[(State, SVOps[SVOp])]]()
    var nF = Map[State, Predecessors[(State, SVOps[SVOp])]]()

    // Keep track of the states already visited
    var visited = Set[State]() + q0

    cF = cF + ((q0, new Predecessors[(State, SVOps[SVOp])]))

    // while it is possible to create a new frontier
    while(cF.size > 0) {

      // advance the frontier, creating connections
      // with each new state's predecessors
      for((q, ps) <- cF) {

        // Get q's outgoing transitions
        val qδ = this.δ.filter((t:Transition[State]) => t.q == q)

        // each new state gets its own list of predecessors
        // all the feasible transitions are followed in order to
        // visit all the graph
        for(t <- qδ) {

          if(t.isInstanceOf[OperationsTransition[State]]) {

            val to = t.asInstanceOf[OperationsTransition[State]]

            var newP = new Predecessors[(State, SVOps[SVOp])]

            // Add any new operations carried by this transition
            // to each predecessor, connect new state to
            // the current state's predecessors
            for(p <- ps) {

              val newSVOps = p._2.union(to.S)
              newP = newP + ((p._1, newSVOps))
              newδ = newδ + new OperationsTransition[State](p._1, newSVOps, this.V, t.q1)

            }

            // Add the current state as predecessor of the
            // target of t
            val newSVOps = (new SVOps[SVOp]) ++ to.S
            newP = newP + ((q, newSVOps))

            // Add the new state to the next frontier
            if (!visited.contains(t.q1)) {

              nF = nF + ((to.q1, newP))
              visited = visited + t.q1
            }
          }
          else if (!visited.contains(t.q1)) {

            // Follow all the transitions!
            nF = nF + ((t.q1, new Predecessors[(State, SVOps[SVOp])]))
            visited = visited + t.q1
          }


        }
      }

      cF = nF
      nF = Map[State, Predecessors[(State, SVOps[SVOp])]]()
    }

    new VSetAutomaton(this.Q, this.q0, this.qf, this.V,  newδ)
  }

  /**
    * Returns the cross product of this transition function with another one.
    * @param other
    * @return
    */
  def ×(other: VSetAutomaton): (TransitionFunction[Transition[State2]], StateSet[State2]) = {

    // The transition function resulting from the product
    var prodδ = new TransitionFunction[Transition[State2]]

    // The state set resulting from the product
    var Q2 = new StateSet[State2]

    // Intersect all possible pairs of transitions
    for(t1 <- this.δ; t2 <- other.δ) {

      val t12Opt = t1 & t2

      // If the intersection succeeded
      if(t12Opt != None) {
        val t12 = t12Opt.get
        prodδ = prodδ + t12

        Q2 = Q2 + t12.q
        Q2 = Q2 + t12.q1
      }

    }

    (prodδ, Q2)
  }

  /**
    * Prunes away unreachable and dead states in the provided transition function and state set.
    * @param δ
    * @param Q
    * @param q0
    * @param qf
    * @tparam State
    * @return
    */
  def prune[State](δ:TransitionFunction[Transition[State]], Q:StateSet[State], q0:State, qf:State):(TransitionFunction[Transition[State]], StateSet[State]) = {

    var forwardδ = Map[State, TransitionFunction[Transition[State]]]()
    var backwardδ = Map[State, TransitionFunction[Transition[State]]]()

    var visitedForward = scala.collection.mutable.Map[State, Boolean]()
    var visitedBackward = scala.collection.mutable.Map[State, Boolean]()

    // Initialize the maps keeping track of visited states
    for(q <- Q) {

      visitedForward += ((q, false))
      visitedBackward += ((q, false))
    }
    visitedForward(q0) = true
    visitedBackward(qf) = true

    // Build forwardδ for forward traversal
    for(q <- Q) {

      val qδ = δ.filter((t:Transition[State]) => t.q == q)
      forwardδ = forwardδ + ((q, qδ))
    }

    // Build backwardδ for backward traversal
    for(q <- Q) {

      val qδ = δ.filter((t:Transition[State]) => t.q1 == q)

      // Reverse the transitions (for the traversal, we don't care about the original label)
      val qδ1: TransitionFunction[Transition[State]] = qδ.map((t: Transition[State]) => new OrdinaryTransition[State](t.q1, 'a', t.V, t.q))

      backwardδ = backwardδ + ((q, qδ1))
    }

    // The two traversals
    traverse(forwardδ, q0, visitedForward)
    traverse(backwardδ, qf, visitedBackward)

    var newQ = new StateSet[State]
    var newδ = new TransitionFunction[Transition[State]]

    // Include only transitions with states visited in both directions
    for(t <- δ) {

      if(visitedForward(t.q) && visitedForward(t.q1) && visitedBackward(t.q) && visitedBackward(t.q1))
        newδ = newδ + t
    }

    // Include only the states visited in both directions
    for(q <- Q) {

      if(visitedForward(q) && visitedBackward(q))
        newQ = newQ + q
    }

    (newδ, newQ)
  }

  /**
    * Traverses the graph defined by the provided transition function, tracking the visited states.
    * @param δ
    * @param q0
    * @param visited
    * @tparam State
    * @return
    */
  def traverse[State](δ:Map[State, TransitionFunction[Transition[State]]], q0:State, visited:scala.collection.mutable.Map[State, Boolean])
    :scala.collection.mutable.Map[State, Boolean] = {

    var ts:TransitionFunction[Transition[State]] = null

    if(δ.contains(q0))
      ts = δ(q0)
    else return visited

    for(t <- ts) {

      if(!visited(t.q1)) {

        visited(t.q1) = true
        traverse[State](δ, t.q1, visited)
      }

    }

    visited

  }

  /**
    * Replaces state pairs with simple states in the provided state set and transition function.
    * @param Q2
    * @param q02
    * @param qf2
    * @param δ2
    * @return
    */
  def State2toState(Q2:StateSet[State2], q02:State2, qf2:State2, δ2:TransitionFunction[Transition[State2]])
  : (StateSet[State], Option[State], Option[State], TransitionFunction[Transition[State]]) = {

    var Q = new StateSet[State]
    var δ = new TransitionFunction[Transition[State]]
    var q0: Option[State] = None
    var qf: Option[State] = None

    var stateMap = Map[State2, State]()

    // Create a new simple state for each state pair
    for(q2 <- Q2) {

      val q = new State
      stateMap = stateMap + ((q2, q))
      Q = Q + q

      if(q2 == q02) q0 = Some(q)
      if(q2 == qf2) qf = Some(q)
    }

    // Create a new transition with the simple states associated to the original state pairs
    // as the new source and destination
    for(t <- δ2) {

      val newQ = stateMap(t.q)
      val newQ1 = stateMap(t.q1)

      t match {

        case OrdinaryTransition(q, σ, v, q1) => {
          δ = δ + new OrdinaryTransition[State](newQ, σ, v, newQ1)
        }
        case RangeTransition(q, σ, v, q1) => {
          δ = δ + new RangeTransition[State](newQ, σ, v, newQ1)
        }
        case OperationsTransition(q, s, v, q1) => {
          δ = δ + new OperationsTransition[State](newQ, s, v, newQ1)
        }
      }
    }

    (Q, q0, qf, δ)
  }

  /**
    * Removes from the autamaton the states that satisfy a given predicate, and the related transitions.
    * @return
    */
  def removeStates(predicate:State => Boolean):VSetAutomaton = {

    var newQ = new StateSet[State]
    var rQ = new StateSet[State]
    var newδ = new TransitionFunction[Transition[State]]
    var rδ = new TransitionFunction[Transition[State]]

    // Accumulate states satyisfing the predicate and related transitions
    for(q <- Q if predicate(q)) {

      rQ = rQ + q
      val tr = δ.filter((t:Transition[State]) => t.q == q || t.q1 == q)
      rδ = rδ ++ tr
    }

    newQ = Q.diff(rQ)
    newδ = δ.diff(rδ)

    new VSetAutomaton(newQ, q0, qf, V, newδ)
  }

  /**
    * Checks if a state only has operations transitions incoming/outgoing.
    * WARNING: won't give positive result for initial and final states!
    * @param q
    * @return
    */
  def hasOnlyOperationsTransitions(q:State):Boolean = {

    // We don't want to accidentally remove the
    // initial or final state
    if(q == q0 || q == qf) return false

    val qδ = δ.filter((t:Transition[State]) => t.q == q || t.q1 == q)

    // Check if there is a non operation transition
    for(t <- qδ if !t.isInstanceOf[OperationsTransition[State]])
      return false
    true
  }
}
