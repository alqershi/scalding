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

import org.apache.hadoop.conf.Configuration

import cascading.flow.FlowConnector
import cascading.flow.hadoop.HadoopFlowConnector
import cascading.flow.local.LocalFlowConnector
import cascading.pipe.Pipe

import scala.collection.JavaConversions._
import cascading.tuple.Tuple
import collection.mutable.Buffer
import collection.mutable.{Map => MMap}

object Mode {
  /**
  * This mode is used by default by sources in read and write
  */
  implicit var mode : Mode = Local()
}
/**
* There are three ways to run jobs
*/
abstract class Mode {
  //We can't name two different pipes with the same name.
  protected val sourceMap = MMap[Source, Pipe]()

  def newFlowConnector(iosers : List[String]) : FlowConnector

  /**
  * Cascading can't handle multiple head pipes with the same
  * name.  This handles them by caching the source and only
  * having a single head pipe to represent each head.
  */
  def getReadPipe(s : Source, p: => Pipe) : Pipe = {
    sourceMap.getOrElseUpdate(s, p)
  }
}

case class Hdfs(val config : Configuration) extends Mode {
  def newFlowConnector(iosersIn : List[String]) = {
    val props = config.foldLeft(Map[AnyRef, AnyRef]()) {
      (acc, kv) => acc + ((kv.getKey, kv.getValue))
    }
    val io = "io.serializations"
    val iosers = (props.get(io).toList ++ iosersIn).mkString(",")
    new HadoopFlowConnector(props + (io -> iosers))
  }
}

case class Local() extends Mode {
  //No serialization is actually done in local mode, it's all memory
  def newFlowConnector(iosers : List[String]) = new LocalFlowConnector
}
/**
* Memory only testing for unit tests
*/
case class Test(val buffers : Map[Source,Buffer[Tuple]]) extends Mode {
  //No serialization is actually done in Test mode, it's all memory
  def newFlowConnector(iosers : List[String]) = new LocalFlowConnector
}
