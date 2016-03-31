package runtime

import scala.collection.mutable.Stack
/**
 * Represents a virtual machine for regular expression programs executions.
 */

package object runtime {
  
  var matched = false
  
}

object vm {

	/**
	 * Executes a regular expression program with a given input.
	 * @param prog the program to execute
	 * @param input the input for the program
	 * @param saved a list of saved input indices for submatch tracking
	 */
	def evaluate(prog: Array[Instruction], input: Array[Int]):Array[Int] =  {

			val len = prog.length
			var cList = new Stack[Thread]()
			var nList = Stack[Thread]()
			var present = new Array[Boolean](len)
			var saved:Array[Int] = null
			var matched = false

			addThread(cList, present, prog(0), 0, new Array[Int](20))

			var i = 0

			for(i <- 0 until input.length) {

				val sp = input(i)
				var j = 0

				var stop = false
				while(cList.length > 0 && !stop) {

					var t = cList.pop

					val pc = t.pc

					pc.opCode match {

						case InstructionType.CHAR => {

							if(sp == pc.c)
								addThread(nList, present, prog(pc.num + 1), i, t.saved)
						}

						case InstructionType.MATCH => {
						  
							saved = t.saved.clone()
							matched = true
							stop = true
						}

						case _ => {

							addThread(nList, present, prog(pc.num), i, t.saved)

						}

					}

					j = j+1

				}

				cList = nList
				nList = Stack[Thread]()
				present = new Array[Boolean](len)

			}
			
			runtime.matched = matched
			saved

	}


	/**
	 * Adds a thread to a given thread stack, only if a thread with the same program counter doesn't exist.
	 * @param l the stack (threads ordered by priority)
	 * @param p an array keeping track of the unique threads already present in l
	 * @param instr the next instruction
	 * @param i the current input character
	 * @param saved the current array of saved string pointers
	 */
	def addThread(l:Stack[Thread], p:Array[Boolean],  instr:Instruction, i:Int, saved:Array[Int]) {

		instr.opCode match {

		case InstructionType.JMP => {

			addThread(l, p, instr.x, i, saved)

		}

		case InstructionType.SPLIT => {

			addThread(l, p, instr.x, i, saved)
			addThread(l, p, instr.y, i, saved)

		}

		case InstructionType.SAVE => {

			saved(instr.i) = i
			addThread(l, p, instr.x, i, saved)

		}

		case _ => {

		  if(!p(instr.num))
				new Thread(instr, saved.clone) +: l;
		}

		}


	}

}