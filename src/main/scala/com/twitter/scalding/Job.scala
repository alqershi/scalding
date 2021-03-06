/*
Copyright 2012 Twitter, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.twitter.scalding

import cascading.flow.{Flow, FlowDef}
import cascading.pipe.Pipe


//For java -> scala implicits on collections
import scala.collection.JavaConversions._

@serializable
class Job(val args : Args) extends TupleConversions with FieldConversions {

  /**
  * you should never call these directly, there are here to make
  * the DSL work.  Just know, you can treat a Pipe as a RichPipe and
  * vice-versa within a Job
  */
  implicit def p2rp(pipe : Pipe) = new RichPipe(pipe)
  implicit def rp2p(rp : RichPipe) = rp.pipe

  //This is the FlowDef used by all Sources this job creates
  @transient
  implicit val flowDef = new FlowDef

  // Use reflection to copy this job:
  def clone(nextargs : Args) : Job = {
    this.getClass
    .getConstructor(classOf[Args])
    .newInstance(nextargs)
    .asInstanceOf[Job]
  }

  /**
  * Implement this method if you want some other jobs to run after the current
  * job. These will not execute until the current job has run successfully.
  */
  def next : Option[Job] = None

  //Only very different styles of Jobs should override this.
  def buildFlow(implicit mode : Mode) = {
    mode.newFlowConnector(ioSerializations ++ List("com.twitter.scalding.KryoHadoopSerialization"))
      .connect(flowDef)
  }
  //Override this if you need to do some extra processing other than complete the flow
  def run(implicit mode : Mode) = {
    val flow = buildFlow(mode)
    flow.complete
    flow.getFlowStats.isSuccessful
  }
  //Add any serializations you need to deal with here:
  def ioSerializations = List[String]()

  //Largely for the benefit of Java jobs
  def read(src : Source) = src.read
  def write(pipe : Pipe, src : Source) {src.write(pipe)}
}

/**
* Sets up an implicit dateRange to use in your sources and an implicit
* timezone.
* Example args: --date 2011-10-02 2011-10-04 --tz UTC
* If no timezone is given, Pacific is assumed.
*/
trait DefaultDateRangeJob extends Job {
  //Get date implicits and PACIFIC and UTC vals.
  import DateOps._

  //optionally take --tz argument, or use Pacific time
  implicit val tz = args.optional("tz") match {
                      case Some(tzn) => java.util.TimeZone.getTimeZone(tzn)
                      case None => PACIFIC
                    }

  val (start, end) = args.list("date") match {
    case List(s, e) => (RichDate(s), RichDate.upperBound(e))
    case List(o) => (RichDate(o), RichDate.upperBound(o))
    case x => error("--date must have exactly one or two date[time]s. Got: " + x.toString)
  }
  //Make sure the end is not before the beginning:
  assert(start <= end, "end of date range must occur after the start")
  implicit val dateRange = DateRange(start, end)
}
