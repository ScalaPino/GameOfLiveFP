/*  _____       _               ____________	*\
** |  __ \     | |              |  ___| ___ \	**
** | |  \/ ___ | |      ______  | |_  | |_/ /	**
** | | __ / _ \| |     |______| |  _| |  __/	**
** | |_\ \ (_) | |____          | |   | |		**
\*  \____/\___/\_____/          \_|   \_|		*/

package org.rosettacode
package pargolfp

import annotation.tailrec

/** The basic virtual game. This object contains the functions with living
 *  cells. The case of combined XYpos: CellsAlive.
 *
 *  @version		0.8 2013-12-31
 *
 *  @author		Frans W. van den Berg
 */
trait CellularAutomaton {
  import XYpos.generation

  /** Detects a stabilization of the number of living cells */
  private def isStablePopulation(pops: GenerationSeq, window: Int): Boolean =
    pops.size >= 2 * window && pops.slice(window, 2 * window).forall(_._1.size == pops.head._1.size)

  /** This is the Game of Live engine
   *
   *  The next generation is composed of newborns from fecund
   *  neighborhoods and adults on stable neighborhoods.
   */
  def tick(population: LivingWorld,
                   rulestringB: Set[Int] = Set(3), // Default to Conway's GoL B3S23
                   rulestringS: Set[Int] = Set(2, 3)): LivingWorld = {
    assume(generation != Int.MaxValue, "Generations outnumbered")
    generation += 1

    /** A Map containing only ''coordinates'' that are neighbors of XYpos which
     *  are alive, together with the ''number'' of XYpos it is neighbor of.
     */
    val neighbors =   
      (population._1.toList.flatMap(_.getMooreNeighborhood)).par.groupBy(identity).map {
        case (cell, coll) => (cell, coll.size)
      }
    
    //println(neighbors)
    // Filter all neighbors for desired characteristics

    // Criterion of rulestring Birth
    def newBorn = neighbors.filter(rulestringB contains _._2).keySet
    // Criterion of Survivors rulestring 
    def survivors = population._1.filter(rulestringS contains neighbors.getOrElse(_, 0))

    return (survivors ++ newBorn, population._2 + 1L)
  } // def tick(…

  private def dummy(dish: GenerationSeq, a: Long, b: Long) = false

  /** Generate a list of PetriDishes, each is a successor of the previous.
   *  Appending is stopped if within the sliding windows the same configuration
   *  of living cells reappears.
   *
   *  @param		orgSeed			The initial living cells configuration.
   *  @param		slidingWindow	The maximal length of returned
   *  @return	The serial sequence of generations in time.
   */
  def getLimitedLifeSeq(orgSeed: LivingWorld,
                        slindingWindowSize: Int,
                        seed: LivingWorld,
                        callback: (GenerationSeq, Long, Long) => Boolean = dummy): GenerationSeq =
    {
      val reference = moveTo(orgSeed)._1
      
      @tailrec
      def inner(pops: GenerationSeq): GenerationSeq = {
         val nextGen = tick(pops.head) +: pops.take(2 * slindingWindowSize - 1)
        // Add last generation in the stream and check for end condition.
        if (nextGen.head._1.isEmpty ||
          moveTo(nextGen.head)._1 == reference ||
          callback(nextGen, nextGen.head._2, 0)) nextGen
        else if (isStablePopulation(nextGen, slindingWindowSize))
          nextGen.drop(nextGen.length - 1)
        else inner(nextGen)
      }
      // Begin of getLifeStream
      val nextGen = inner(collection.parallel.ParSeq(seed)).par.reverse
      nextGen.drop((nextGen.length - slindingWindowSize) max 0)
    } // def getLimitedLifeSeq(…

  /** Determine the envelope of all cells in a generation*/
  def boundingBox(gen: LivingWorld): Rect = {
    if (gen._1.isEmpty)
      throw new UnsupportedOperationException("empty.boundingBox")
    // Aggregate each XYpos to maximum extreme
    //TODO: Check use of method TrieMap.aggregate
    gen._1.foldLeft(gen._1.head extreme (gen._1.head))(
      (resultingRect, currentPos) => (currentPos extreme resultingRect))
  }

  /** Moves the pattern without altering its disposition
   */
   protected def moveTo(gen: LivingWorld, center: XYpos = (0, 0)): LivingWorld = {
    val extremes = boundingBox(gen)
    val offset = XYpos(
      extremes._1.x + (extremes._2.x - extremes._1.x) / 2 - center.x,
      extremes._1.y + (extremes._2.y - extremes._1.y) / 2 - center.y)
    (gen._1.map(_ - offset), gen._2)
  } // def moveTo(…

  /** Remove unused XYpos from the cache while keeping given generations.*/
  def flushCache(threshold: Int) {
    val absThreshold = generation - threshold
    if (absThreshold <= generation) { // Prevent underflow
      for (elem ← XYpos.cache.seq)
        if (absThreshold >= elem._2.timestamp) XYpos.cache -= elem._1
    }
  }
} // trait CellularAutomaton

// ############################################################################