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

package uk.gov.hmrc.agentregistrationfrontend.controllers.applicant.amls

import uk.gov.hmrc.agentregistrationfrontend.model.upscan.Upload
import uk.gov.hmrc.agentregistrationfrontend.model.upscan.UploadNotificationRequest
import uk.gov.hmrc.agentregistrationfrontend.repository.UploadRepo
import uk.gov.hmrc.agentregistrationfrontend.testsupport.ControllerSpec
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.client.HttpClientV2
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw

class NotificationFromUpscanControllerSpec
extends ControllerSpec:

  private val call: Call = AppRoutes.apply.amls.api.NotificationFromUpscanController.processNotificationFromUpscan(tdAll.uploadId)

  final case class TestCase(
    uploadNotificationRequest: UploadNotificationRequest,
    expectedUploadAfterUpdate: Upload
  )

  List(
    TestCase(
      uploadNotificationRequest = tdAll.uploadNotificationRequestSucceeded,
      expectedUploadAfterUpdate = tdAll.uploadUploadedSuccessfully
    ),
    TestCase(
      uploadNotificationRequest = tdAll.uploadNotificationRequestFailed,
      expectedUploadAfterUpdate = tdAll.uploadFailed
    )
  ).foreach: tc =>
    s"${tc.uploadNotificationRequest.getClass.getSimpleName} notification from Upscan to $call should update Upload to ${tc.expectedUploadAfterUpdate.uploadStatus.getClass.getSimpleName}" in:
      val uploadInProgress: Upload = tdAll.uploadInProgress
      val uploadAfterUpdate: Upload = tdAll.uploadUploadedSuccessfully
      val uploadNotificationRequest = tdAll.uploadNotificationRequestSucceeded

      val uploadRepo: UploadRepo = app.injector.instanceOf[UploadRepo]
      withClue("There must be an Upload in progress with corresponding fileUploadReference"):
        uploadRepo.drop().futureValue
        uploadRepo.upsert(uploadInProgress).futureValue
        uploadNotificationRequest.reference shouldBe uploadInProgress.fileUploadReference
        uploadRepo.findLatestByInternalUserId(tdAll.internalUserId).futureValue.value shouldBe uploadInProgress

      val httpClient: HttpClientV2 = app.injector.instanceOf[HttpClientV2]

      val response: HttpResponse =
        httpClient
          .post(
            url"${thisFrontendBaseUrl + AppRoutes.apply.amls.api.NotificationFromUpscanController.processNotificationFromUpscan(tdAll.uploadId).url}"
          )(using tdAll.headerCarrier)
          .withBody(tdAll.uploadNotificationRequestSucceededJson)
          .execute[HttpResponse]
          .futureValue

      response.status shouldBe Status.OK
      response.body shouldBe Constants.EMPTY_STRING
      uploadRepo.findLatestByInternalUserId(tdAll.internalUserId).futureValue.value shouldBe uploadAfterUpdate
