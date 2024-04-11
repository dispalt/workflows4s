package workflow4s.example

import workflow4s.wio.WorkflowContext

package object docs {

  case class MyEvent()
  case class MyState(counter: Int)
  case class MyError()
  case class MyRequest()
  case class MyResponse()

  object Context extends WorkflowContext {
    override type Event = MyEvent
    override type State = MyState
  }



}
