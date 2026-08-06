/*
 * Copyright 2025 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.agentregistrationfrontend.controllers.applicant.listdetails.nonincorporated

import com.google.inject.Inject
import com.google.inject.Singleton
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.MessagesControllerComponents
import play.api.mvc.Result
import uk.gov.hmrc.agentregistration.shared.*
import uk.gov.hmrc.agentregistration.shared.AgentApplicationSoleTrader
import uk.gov.hmrc.agentregistration.shared.AgentApplication.IsAgentApplicationForDeclaringNumberOfKeyIndividuals
import uk.gov.hmrc.agentregistration.shared.AgentApplication.IsIncorporated
import uk.gov.hmrc.agentregistration.shared.lists.NumberOfRequiredKeyIndividuals
import uk.gov.hmrc.agentregistration.shared.individual.IndividualProvidedDetails
import uk.gov.hmrc.agentregistrationfrontend.action.applicant.ApplicantActions
import uk.gov.hmrc.agentregistrationfrontend.controllers.applicant.FrontendController
import uk.gov.hmrc.agentregistrationfrontend.services.individual.IndividualProvideDetailsService
import uk.gov.hmrc.agentregistrationfrontend.views.html.applicant.listdetails.nonincorporated.CheckYourAnswersPage

@Singleton
class CheckYourAnswersController @Inject() (
  mcc: MessagesControllerComponents,
  actions: ApplicantActions,
  view: CheckYourAnswersPage,
  individualProvideDetailsService: IndividualProvideDetailsService
)
extends FrontendController(mcc, actions):

  private type DataWithLists =
    List[
      IndividualProvidedDetails
    ] *: NumberOfRequiredKeyIndividuals *: BusinessPartnerRecordResponse *: IsAgentApplicationForDeclaringNumberOfKeyIndividuals *: DataWithAuth

  private val baseAction: ActionBuilderWithData[DataWithLists] = actions
    .getApplicationInProgress
    .getBusinessPartnerRecord
    .refine:
      implicit request =>
        request.get[AgentApplication] match
          case _: IsIncorporated =>
            logger.warn(
              "Incorporated businesses should be name matching key individuals against Companies House results, redirecting to task list for the correct links"
            )
            Redirect(AppRoutes.apply.TaskListController.show.url)
          case _: AgentApplicationSoleTrader =>
            logger.warn("Sole traders do not add individuals to a list, redirecting to task list for the correct links")
            Redirect(AppRoutes.apply.TaskListController.show.url)
          case aa: IsAgentApplicationForDeclaringNumberOfKeyIndividuals =>
            request.replace[AgentApplication, IsAgentApplicationForDeclaringNumberOfKeyIndividuals](aa)
    .refine:
      implicit request =>
        request.get[IsAgentApplicationForDeclaringNumberOfKeyIndividuals].getNumberOfRequiredKeyIndividuals match
          case Some(n: NumberOfRequiredKeyIndividuals) => request.add(n)
          case None =>
            logger.debug(
              "Number of required key individuals not specified in application, redirecting to number of key individuals page"
            )
            Redirect(AppRoutes.apply.listdetails.nonincorporated.NumberOfKeyIndividualsController.show.url)
    .refine:
      implicit request =>
        val agentApplication: IsAgentApplicationForDeclaringNumberOfKeyIndividuals = request.get
        individualProvideDetailsService
          .findAllKeyIndividualsByApplicationId(
            agentApplication.agentApplicationId
          ).map[RequestWithData[
            DataWithLists
          ] | Result]:
            case Nil if request.get[NumberOfRequiredKeyIndividuals].totalListSize > 0 =>
              logger.warn(
                "Number of required key individuals specified in application, but no individuals found, redirecting to number of enter key individual page"
              )
              Redirect(AppRoutes.apply.listdetails.nonincorporated.EnterKeyIndividualController.show.url)
            case Nil =>
              logger.warn(
                "Number of required key individuals specified in application is zero, skipping CYA and redirecting to main CYA to see if other individuals needed"
              )
              Redirect(AppRoutes.apply.listdetails.CheckYourAnswersController.show.url)
            case list: List[IndividualProvidedDetails] => request.add[List[IndividualProvidedDetails]](list)

  def show: Action[AnyContent] = baseAction:
    implicit request =>
      val application: IsAgentApplicationForDeclaringNumberOfKeyIndividuals = request.get[IsAgentApplicationForDeclaringNumberOfKeyIndividuals]
      Ok(view(
        numberOfKeyIndividuals = application.getNumberOfRequiredKeyIndividuals.getOrThrowExpectedDataMissing("NumberOfRequiredKeyIndividuals"),
        existingList = request.get[List[IndividualProvidedDetails]],
        entityName = request.get[BusinessPartnerRecordResponse].getEntityName
      ))
