package workflow4s.wio

import cats.effect.IO
import workflow4s.wio.internal.{EventHandler, SignalHandler}
import workflow4s.wio.model.ModelUtils

import scala.reflect.ClassTag

object InterruptionBuilder {

  class Step0[Ctx <: WorkflowContext]() {
    type Input = WCState[Ctx]

    def throughSignal[Req, Resp](signalDef: SignalDef[Req, Resp]): Step1[Req, Resp] = Step1[Req, Resp](signalDef)

    class Step1[Req, Resp](signalDef: SignalDef[Req, Resp]) {

      def handleAsync[Evt <: WCEvent[Ctx]](f: (Input, Req) => IO[Evt])(implicit evtCt: ClassTag[Evt]): Step2[Evt] = Step2(f, evtCt)

      def handleSync[Evt <: WCEvent[Ctx]](f: (Input, Req) => Evt)(implicit evtCt: ClassTag[Evt]): Step2[Evt] =
        Step2((x, y) => IO.pure(f(x, y)), evtCt)

      class Step2[Evt <: WCEvent[Ctx]](signalHandler: (Input, Req) => IO[Evt], evtCt: ClassTag[Evt]) {

        def handleEvent[Out <: WCState[Ctx]](f: (Input, Evt) => Out): Step3[Nothing, Out] =
          Step3({ (x, y) => Right(f(x, y)) }, ErrorMeta.noError)

        def handleEventWithError[Err, Out <: WCState[Ctx]](f: (Input, Evt) => Either[Err, Out])(implicit
            errorMeta: ErrorMeta[Err],
        ): Step3[Err, Out] =
          Step3(f, errorMeta)

        class Step3[Err, Out <: WCState[Ctx]](
            eventHandler: (Input, Evt) => Either[Err, Out],
            errorMeta: ErrorMeta[Err],
        ) {

          def produceResponse(f: (Input, Evt) => Resp): Step4 = Step4(f, None, None)
          def voidResponse(using ev: Unit =:= Resp): Step4    = Step4((x, y) => (), None, None)

          class Step4(responseBuilder: (Input, Evt) => Resp, operationName: Option[String], signalName: Option[String]) {

            def named(operationName: String = null, signalName: String = null): Step4 =
              Step4(responseBuilder, Option(operationName).orElse(this.operationName), Option(signalName).orElse(this.signalName))

            def autoNamed()(using n: sourcecode.Name): Step4 = named(operationName = ModelUtils.prettifyName(n.value))

            def andThen[FinalErr, FinalOut <: WCState[Ctx]](
                f: WIO[Input, Err, Out, Ctx] => WIO[Input, FinalErr, FinalOut, Ctx],
            ): Step5[FinalErr, FinalOut] = Step5(f)
            def noFollowupSteps: Step5[Err, Out] = Step5(identity)

            class Step5[FinalErr, FinalOut <: WCState[Ctx]](buildFinalWIO: WIO[Input, Err, Out, Ctx] => WIO[Input, FinalErr, FinalOut, Ctx]) {

              def done: WIO.Interruption[Ctx, FinalErr, FinalOut, Out, Err] = {
                val combined: (Input, Evt) => (Either[Err, Out], Resp)                   = (s: Input, e: Evt) => (eventHandler(s, e), responseBuilder(s, e))
                val eh: EventHandler[Input, (Either[Err, Out], Resp), WCEvent[Ctx], Evt] = EventHandler(evtCt.unapply, identity, combined)
                val sh: SignalHandler[Req, Evt, Input]                                   = SignalHandler(signalHandler)(signalDef.reqCt)
                val meta                                                                 = WIO.HandleSignal.Meta(errorMeta, signalName.getOrElse(ModelUtils.getPrettyNameForClass(signalDef.reqCt)), operationName)
                val handleSignal: WIO.HandleSignal[Ctx, Input, Out, Err, Req, Resp, Evt] = WIO.HandleSignal(signalDef, sh, eh, meta)
                WIO.Interruption(
                  handleSignal,
                  buildFinalWIO,
                )
              }

            }

          }

        }

      }

    }
  }

}
