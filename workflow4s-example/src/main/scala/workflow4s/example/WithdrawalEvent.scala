package workflow4s.example

import workflow4s.example.WithdrawalService.{ExecutionResponse, Fee, Iban}
import workflow4s.example.checks.ChecksEvent

sealed trait WithdrawalEvent
object WithdrawalEvent {
  sealed trait ValidationResult                                      extends WithdrawalEvent with Product with Serializable
  case class WithdrawalAccepted(amount: BigDecimal, recipient: Iban) extends ValidationResult
  case class WithdrawalRejected(error: String)                       extends ValidationResult

  case class FeeSet(fee: Fee) extends WithdrawalEvent

  sealed trait MoneyLocked extends WithdrawalEvent with Product with Serializable
  object MoneyLocked {
    case class Success()        extends MoneyLocked
    case class NotEnoughFunds() extends MoneyLocked
  }

  case class ChecksRun(inner: ChecksEvent) extends WithdrawalEvent

  case class ExecutionInitiated(response: ExecutionResponse) extends WithdrawalEvent

  case class ExecutionCompleted(status: WithdrawalSignal.ExecutionCompleted) extends WithdrawalEvent

  case class MoneyReleased() extends WithdrawalEvent
  case class RejectionHandled(error: String) extends WithdrawalEvent
}
