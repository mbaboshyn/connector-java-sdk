package eu.enmeshed;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import eu.enmeshed.client.EnmeshedClient;
import eu.enmeshed.model.AttributeWrapper;
import eu.enmeshed.model.ContentWrapper;
import eu.enmeshed.model.IdentityInfo;
import eu.enmeshed.model.Response;
import eu.enmeshed.model.ResultWrapper;
import eu.enmeshed.model.attributes.Attribute;
import eu.enmeshed.model.attributes.IdentityAttribute;
import eu.enmeshed.model.attributes.values.AttributeValue;
import eu.enmeshed.model.attributes.values.identity.BirthDate;
import eu.enmeshed.model.attributes.values.identity.BirthYear;
import eu.enmeshed.model.attributes.values.identity.DisplayName;
import eu.enmeshed.model.attributes.values.identity.EMailAddress;
import eu.enmeshed.model.attributes.values.identity.GivenName;
import eu.enmeshed.model.attributes.values.identity.Surname;
import eu.enmeshed.model.relationshipTemplates.RelationshipTemplate;
import eu.enmeshed.model.relationshipTemplates.RelationshipTemplateContent;
import eu.enmeshed.model.relationshipTemplates.RelationshipTemplateCreation;
import eu.enmeshed.model.relationships.Relationship;
import eu.enmeshed.model.relationships.RelationshipChange;
import eu.enmeshed.model.relationships.RelationshipChangeRequest;
import eu.enmeshed.model.relationships.RelationshipCreationChangeRequestContent;
import eu.enmeshed.model.requestItems.CreateAttributeRequestItem;
import eu.enmeshed.model.requestItems.ReadAttributeRequestItem;
import eu.enmeshed.model.requestItems.RequestItem;
import eu.enmeshed.model.requestItems.ShareAttributeRequestItem;
import eu.enmeshed.model.responseItems.ReadAttributeAcceptResponseItem;
import eu.enmeshed.model.responseItems.ResponseItem;
import eu.enmeshed.model.responseItems.ResponseItemGroup;
import feign.Request;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EnmeshedOnboardingServiceTest {

  private static final String CONNECTOR_DISPLAY_NAME = "Test Connector";
  private static final List<Class<? extends AttributeValue>> REQUIRED_ATTRIBUTES =
      List.of(GivenName.class, Surname.class);
  private static final List<Class<? extends AttributeValue>> OPTIONAL_ATTRIBUTES =
      List.of(EMailAddress.class);
  private final List<Class<? extends RequestItem>> CREATE_ATTRIBUTES =
      List.of(CreateAttributeRequestItem.class);

  private static final IdentityInfo TEST_IDENTITY_INFO =
      IdentityInfo.builder()
          .address("da88dd1b2b820360d4155162e657f84ea1394076faa1ce2909d8338811cb308d")
          .publicKey("dbb5d8fd21caf827fdc128d73e783478d6677a9afc50120db56217726125425f")
          .realm("4354b5ae54bab15544f852d7bc1b76bbd6d71a03b5e7ad876916cda3a602aaf9")
          .build();
  @Mock EnmeshedClient enmeshedClientMock;
  @Captor ArgumentCaptor<ContentWrapper<Attribute>> attributeCreateRequestCaptor;
  @Captor ArgumentCaptor<RelationshipTemplateCreation> relationshipTemplateCreationArgumentCaptor;
  EnmeshedOnboardingService enmeshedService;

  @Test
  void itShouldProvideIdentityInfoAndReuseConfiguredDisplayNameIfSet() {

    when(enmeshedClientMock.getIdentityInfo())
        .thenReturn(ResultWrapper.containing(TEST_IDENTITY_INFO));

    AttributeWrapper wrappedDisplayNameAttribute =
        AttributeWrapper.builder()
            .id("ATTR_ID")
            .content(
                IdentityAttribute.builder()
                    .owner(TEST_IDENTITY_INFO.getAddress())
                    .value(DisplayName.builder().value(CONNECTOR_DISPLAY_NAME).build())
                    .build())
            .build();

    when(enmeshedClientMock.searchAttributes(anyString(), anyString(), anyString()))
        .thenReturn(ResultWrapper.containing(List.of(wrappedDisplayNameAttribute)));

    enmeshedService =
        new EnmeshedOnboardingService(
            enmeshedClientMock, CONNECTOR_DISPLAY_NAME, REQUIRED_ATTRIBUTES, OPTIONAL_ATTRIBUTES);

    Assertions.assertEquals(TEST_IDENTITY_INFO, enmeshedService.getIdentityInfo());
    Assertions.assertEquals(
        CONNECTOR_DISPLAY_NAME,
        ((DisplayName) enmeshedService.getConnectorDisplayNameAttribute().getContent().getValue())
            .getValue());
    verify(enmeshedClientMock, never()).createAttribute(any());
  }

  @Test
  void itShouldProvideIdentityInfoAndConfigureItsDisplayNameIfNotSet() {

    when(enmeshedClientMock.getIdentityInfo())
        .thenReturn(ResultWrapper.containing(TEST_IDENTITY_INFO));

    AttributeWrapper wrappedDisplayNameAttribute =
        AttributeWrapper.builder()
            .id("ATTR_ID")
            .content(
                IdentityAttribute.builder()
                    .owner(TEST_IDENTITY_INFO.getAddress())
                    .value(DisplayName.builder().value(CONNECTOR_DISPLAY_NAME).build())
                    .build())
            .build();

    when(enmeshedClientMock.createAttribute(attributeCreateRequestCaptor.capture()))
        .thenReturn(ResultWrapper.containing(wrappedDisplayNameAttribute));

    when(enmeshedClientMock.searchAttributes(anyString(), anyString(), anyString()))
        .thenReturn(ResultWrapper.containing(Collections.emptyList()));

    enmeshedService =
        new EnmeshedOnboardingService(
            enmeshedClientMock, CONNECTOR_DISPLAY_NAME, REQUIRED_ATTRIBUTES, OPTIONAL_ATTRIBUTES);

    Assertions.assertEquals(TEST_IDENTITY_INFO, enmeshedService.getIdentityInfo());
    Assertions.assertEquals(
        CONNECTOR_DISPLAY_NAME,
        ((DisplayName) enmeshedService.getConnectorDisplayNameAttribute().getContent().getValue())
            .getValue());
    verify(enmeshedClientMock).createAttribute(any());

    Assertions.assertInstanceOf(
        DisplayName.class, attributeCreateRequestCaptor.getValue().getContent().getValue());
    Assertions.assertEquals(
        CONNECTOR_DISPLAY_NAME,
        ((DisplayName) attributeCreateRequestCaptor.getValue().getContent().getValue()).getValue());
    Assertions.assertEquals(
        TEST_IDENTITY_INFO.getAddress(),
        attributeCreateRequestCaptor.getValue().getContent().getOwner());
  }

  @Test
  void itShouldReturnRegistrationData() {

    enmeshedService = getServiceInstance();

    byte[] testQrCodeData = new byte[] {0xd, 0xe, 0xa, 0xd, 0xb, 0xe, 0xe, 0xf};
    String testRelationshipTemplateId = "RLT_ID";
    String testDisplayNameRequestedAttributes = "Requested Attributes";
    String testDisplayNameSharedAttributes = "Shared Attributes";
    String testDisplayNameCreateAttributes = "Create Attributes";

    when(enmeshedClientMock.createOwnRelationshipTemplate(
            relationshipTemplateCreationArgumentCaptor.capture()))
        .then(
            mockInvocation ->
                ResultWrapper.containing(
                    RelationshipTemplate.builder()
                        .id(testRelationshipTemplateId)
                        .isOwn(true)
                        .content(
                            mockInvocation
                                .getArgument(0, RelationshipTemplateCreation.class)
                                .getContent())
                        .build()));

    when(enmeshedClientMock.getQrCodeForRelationshipTemplate(testRelationshipTemplateId))
        .thenReturn(
            feign.Response.builder()
                .status(200)
                .body(testQrCodeData)
                .request(
                    Request.create(
                        Request.HttpMethod.GET, "", Collections.emptyMap(), null, null, null))
                .build());

    EnmeshedOnboardingService.RegistrationData registrationData =
        enmeshedService.generateQrCodeForRegistrationAsJpg(
            testDisplayNameRequestedAttributes,
            testDisplayNameSharedAttributes,
            testDisplayNameCreateAttributes,
            null);

    Assertions.assertArrayEquals(testQrCodeData, registrationData.qrCode());
    Assertions.assertEquals(testRelationshipTemplateId, registrationData.relationshipTemplateId());

    Assertions.assertEquals(
        1, relationshipTemplateCreationArgumentCaptor.getValue().getMaxNumberOfAllocations());

    // At least one hour in future (with 5s tolerance)
    Assertions.assertTrue(
        Instant.from(relationshipTemplateCreationArgumentCaptor.getValue().getExpiresAt())
                    .toEpochMilli()
                - Instant.now().toEpochMilli()
            > 3_595_000);

    RelationshipTemplateContent.ItemList itemList =
        relationshipTemplateCreationArgumentCaptor.getValue().getContent().getOnNewRelationship();

    // Shared Items
    Assertions.assertTrue(itemList.items().get(0).getItems().get(0).getMustBeAccepted());
    Assertions.assertEquals(testDisplayNameSharedAttributes, itemList.items().get(0).getTitle());
    Assertions.assertEquals(
        CONNECTOR_DISPLAY_NAME,
        ((DisplayName)
                ((ShareAttributeRequestItem) itemList.items().get(0).getItems().get(0))
                    .getAttribute()
                    .getValue())
            .getValue());
    Assertions.assertTrue(itemList.items().get(0).getItems().get(0).getMustBeAccepted());

    // Required Items
    Assertions.assertEquals(testDisplayNameRequestedAttributes, itemList.items().get(1).getTitle());
    Assertions.assertTrue(itemList.items().get(1).getItems().get(0).getMustBeAccepted());
    Assertions.assertEquals(
        REQUIRED_ATTRIBUTES.get(0).getSimpleName(),
        ((ReadAttributeRequestItem) itemList.items().get(1).getItems().get(0))
            .getQuery()
            .get("valueType"));
    Assertions.assertTrue(itemList.items().get(1).getItems().get(0).getMustBeAccepted());
    Assertions.assertEquals(
        REQUIRED_ATTRIBUTES.get(1).getSimpleName(),
        ((ReadAttributeRequestItem) itemList.items().get(1).getItems().get(1))
            .getQuery()
            .get("valueType"));
    Assertions.assertTrue(itemList.items().get(1).getItems().get(1).getMustBeAccepted());

    // Optional Items
    Assertions.assertFalse(itemList.items().get(1).getItems().get(2).getMustBeAccepted());
    Assertions.assertEquals(
        OPTIONAL_ATTRIBUTES.get(0).getSimpleName(),
        ((ReadAttributeRequestItem) itemList.items().get(1).getItems().get(2))
            .getQuery()
            .get("valueType"));
    Assertions.assertFalse(itemList.items().get(1).getItems().get(2).getMustBeAccepted());

    // Created Items
    Assertions.assertTrue(itemList.items().get(2).getItems().get(0).getMustBeAccepted());
    Assertions.assertEquals(CREATE_ATTRIBUTES.get(0).getSimpleName(), "CreateAttributeRequestItem");
  }

  @Test
  void itShouldReturnNullIfRelationshipTemplateCouldNotBeFound() {

    String relationshipTemplateId = "RLTXXX";
    enmeshedService = getServiceInstance();

    when(enmeshedClientMock.searchRelationships(any(), any(), any()))
        .thenReturn(ResultWrapper.containing(Collections.emptyList()));

    Assertions.assertNull(enmeshedService.checkRegistrationState(relationshipTemplateId));

    verify(enmeshedClientMock).sync();
  }

  @Test
  void itShouldReturnRegistrationDataIfRelationshipTemplateCouldBeFound() {

    String relationshipTemplateId = "RLT_XXX";
    String relationshipChangeId = "RCH_XXX";
    String relationshipId = "REL_XXX";
    String userGivenName = "Max";
    String userSurname = "Muster";
    String userAddress = "ADDR_XXX";
    Integer userBirthyear = 2000;
    enmeshedService = getServiceInstance();

    when(enmeshedClientMock.searchRelationships(eq(relationshipTemplateId), any(), any()))
        .thenReturn(
            ResultWrapper.containing(
                List.of(
                    Relationship.builder()
                        .id(relationshipId)
                        .template(RelationshipTemplate.builder().id(relationshipTemplateId).build())
                        .peerIdentity(IdentityInfo.builder().address(userAddress).build())
                        .peer(userAddress)
                        .changes(
                            List.of(
                                RelationshipChange.builder()
                                    .id(relationshipChangeId)
                                    .type(RelationshipChange.Type.CREATION)
                                    .status(RelationshipChange.Status.ACCEPTED)
                                    .request(
                                        RelationshipChangeRequest.builder()
                                            .content(
                                                RelationshipCreationChangeRequestContent.builder()
                                                    .response(
                                                        Response.builder()
                                                            .items(
                                                                List.of(
                                                                    ResponseItemGroup.builder()
                                                                        .result(
                                                                            ResponseItem.Result
                                                                                .ACCEPTED)
                                                                        .items(
                                                                            List.of(
                                                                                ReadAttributeAcceptResponseItem
                                                                                    .builder()
                                                                                    .result(
                                                                                        ResponseItem
                                                                                            .Result
                                                                                            .ACCEPTED)
                                                                                    .attribute(
                                                                                        IdentityAttribute
                                                                                            .builder()
                                                                                            .value(
                                                                                                GivenName
                                                                                                    .builder()
                                                                                                    .value(
                                                                                                        userGivenName)
                                                                                                    .build())
                                                                                            .build())
                                                                                    .build(),
                                                                                ReadAttributeAcceptResponseItem
                                                                                    .builder()
                                                                                    .result(
                                                                                        ResponseItem
                                                                                            .Result
                                                                                            .ACCEPTED)
                                                                                    .attribute(
                                                                                        IdentityAttribute
                                                                                            .builder()
                                                                                            .value(
                                                                                                Surname
                                                                                                    .builder()
                                                                                                    .value(
                                                                                                        userSurname)
                                                                                                    .build())
                                                                                            .build())
                                                                                    .build(),
                                                                                ReadAttributeAcceptResponseItem
                                                                                    .builder()
                                                                                    .result(
                                                                                        ResponseItem
                                                                                            .Result
                                                                                            .ACCEPTED)
                                                                                    .attribute(
                                                                                        IdentityAttribute
                                                                                            .builder()
                                                                                            .value(
                                                                                                BirthYear
                                                                                                    .builder()
                                                                                                    .value(
                                                                                                        userBirthyear)
                                                                                                    .build())
                                                                                            .build())
                                                                                    .build()))
                                                                        .build()))
                                                            .requestId("REQ_ID")
                                                            .result(
                                                                eu.enmeshed.model.Response.Result
                                                                    .ACCEPTED)
                                                            .build())
                                                    .build())
                                            .build())
                                    .build()))
                        .build())));

    EnmeshedOnboardingService.RegistrationResult registrationResult =
        enmeshedService.checkRegistrationState(relationshipTemplateId);

    Assertions.assertEquals(userAddress, registrationResult.enmeshedAddress());
    Assertions.assertEquals(relationshipId, registrationResult.relationshipId());
    Assertions.assertEquals(relationshipChangeId, registrationResult.relationshipChangeId());
    Assertions.assertEquals(
        userGivenName,
        ((GivenName) registrationResult.attributes().get(GivenName.class)).getValue());
    Assertions.assertEquals(
        userSurname, ((Surname) registrationResult.attributes().get(Surname.class)).getValue());
    Assertions.assertEquals(
        userBirthyear,
        ((BirthYear) registrationResult.attributes().get(BirthYear.class)).getValue());

    InOrder inOrder = Mockito.inOrder(enmeshedClientMock);
    inOrder.verify(enmeshedClientMock).sync();
    inOrder
        .verify(enmeshedClientMock)
        .searchRelationships(eq(relationshipTemplateId), any(), any());

    verify(enmeshedClientMock, never()).acceptRelationshipChange(anyString(), anyString(), any());
  }

  @Test
  void itShouldAcceptTheIncomingRequestAndReturnSentData() {

    String relationshipTemplateId = "RLT_XXX";
    String relationshipChangeId = "RCH_XXX";
    String relationshipId = "REL_XXX";
    String userGivenName = "Max";
    String userSurname = "Muster";
    String userAddress = "ADDR_XXX";
    int userBirthDay = 12;
    int userBirthMonth = 12;
    int userBirthYear = 1999;
    enmeshedService = getServiceInstance();

    RelationshipChangeRequest relationshipChangeRequest =
        RelationshipChangeRequest.builder()
            .content(
                RelationshipCreationChangeRequestContent.builder()
                    .response(
                        Response.builder()
                            .items(
                                List.of(
                                    ReadAttributeAcceptResponseItem.builder()
                                        .result(ResponseItem.Result.ACCEPTED)
                                        .attribute(
                                            IdentityAttribute.builder()
                                                .value(
                                                    GivenName.builder()
                                                        .value(userGivenName)
                                                        .build())
                                                .build())
                                        .build(),
                                    ReadAttributeAcceptResponseItem.builder()
                                        .result(ResponseItem.Result.ACCEPTED)
                                        .attribute(
                                            IdentityAttribute.builder()
                                                .value(Surname.builder().value(userSurname).build())
                                                .build())
                                        .build(),
                                    ReadAttributeAcceptResponseItem.builder()
                                        .result(ResponseItem.Result.ACCEPTED)
                                        .attribute(
                                            IdentityAttribute.builder()
                                                .value(
                                                    BirthDate.builder()
                                                        .day(userBirthDay)
                                                        .month(userBirthMonth)
                                                        .year(userBirthYear)
                                                        .build())
                                                .build())
                                        .build()))
                            .requestId("REQ_ID")
                            .result(Response.Result.ACCEPTED)
                            .build())
                    .build())
            .build();

    when(enmeshedClientMock.searchRelationships(eq(relationshipTemplateId), any(), any()))
        .thenReturn(
            ResultWrapper.containing(
                List.of(
                    Relationship.builder()
                        .id(relationshipId)
                        .template(RelationshipTemplate.builder().id(relationshipTemplateId).build())
                        .peerIdentity(IdentityInfo.builder().address(userAddress).build())
                        .peer(userAddress)
                        .changes(
                            List.of(
                                RelationshipChange.builder()
                                    .id(relationshipChangeId)
                                    .type(RelationshipChange.Type.CREATION)
                                    .status(RelationshipChange.Status.PENDING)
                                    .request(relationshipChangeRequest)
                                    .build()))
                        .build())))
        .thenReturn(
            ResultWrapper.containing(
                List.of(
                    Relationship.builder()
                        .id(relationshipId)
                        .template(RelationshipTemplate.builder().id(relationshipTemplateId).build())
                        .peerIdentity(IdentityInfo.builder().address(userAddress).build())
                        .peer(userAddress)
                        .changes(
                            List.of(
                                RelationshipChange.builder()
                                    .id(relationshipChangeId)
                                    .type(RelationshipChange.Type.CREATION)
                                    .status(RelationshipChange.Status.ACCEPTED)
                                    .request(relationshipChangeRequest)
                                    .build()))
                        .build())));

    EnmeshedOnboardingService.RegistrationResult registrationResult =
        enmeshedService.checkRegistrationState(relationshipTemplateId);
    BirthDate birthDate = (BirthDate) registrationResult.attributes().get(BirthDate.class);

    Assertions.assertEquals(userAddress, registrationResult.enmeshedAddress());
    Assertions.assertEquals(relationshipId, registrationResult.relationshipId());
    Assertions.assertEquals(relationshipChangeId, registrationResult.relationshipChangeId());
    Assertions.assertEquals(
        userGivenName,
        ((GivenName) registrationResult.attributes().get(GivenName.class)).getValue());
    Assertions.assertEquals(
        userSurname, ((Surname) registrationResult.attributes().get(Surname.class)).getValue());
    Assertions.assertEquals(userBirthDay, birthDate.getDay());
    Assertions.assertEquals(userBirthMonth, birthDate.getMonth());
    Assertions.assertEquals(userBirthYear, birthDate.getYear());
    Assertions.assertTrue(registrationResult.accepted());
    Assertions.assertEquals(3, registrationResult.attributes().size());

    InOrder inOrder = Mockito.inOrder(enmeshedClientMock);
    inOrder.verify(enmeshedClientMock).sync();
    inOrder
        .verify(enmeshedClientMock)
        .searchRelationships(eq(relationshipTemplateId), any(), any());
    inOrder
        .verify(enmeshedClientMock)
        .acceptRelationshipChange(eq(relationshipId), eq(relationshipChangeId), any());
    inOrder.verify(enmeshedClientMock).sync();
    inOrder
        .verify(enmeshedClientMock)
        .searchRelationships(eq(relationshipTemplateId), any(), any());
  }

  @Test
  void itShouldRejectTheIncomingRequestAndReturnSentData() {

    String relationshipTemplateId = "RLT_XXX";
    String relationshipChangeId = "RCH_XXX";
    String relationshipId = "REL_XXX";
    String userGivenName = "Max";
    String userSurname = "Muster";
    String userAddress = "ADDR_XXX";
    enmeshedService = getServiceInstance();

    RelationshipChangeRequest relationshipChangeRequest =
        RelationshipChangeRequest.builder()
            .content(
                RelationshipCreationChangeRequestContent.builder()
                    .response(
                        Response.builder()
                            .items(
                                List.of(
                                    ReadAttributeAcceptResponseItem.builder()
                                        .result(ResponseItem.Result.ACCEPTED)
                                        .attribute(
                                            IdentityAttribute.builder()
                                                .value(
                                                    GivenName.builder()
                                                        .value(userGivenName)
                                                        .build())
                                                .build())
                                        .build(),
                                    ReadAttributeAcceptResponseItem.builder()
                                        .result(ResponseItem.Result.ACCEPTED)
                                        .attribute(
                                            IdentityAttribute.builder()
                                                .value(Surname.builder().value(userSurname).build())
                                                .build())
                                        .build()))
                            .requestId("REQ_ID")
                            .result(Response.Result.ACCEPTED)
                            .build())
                    .build())
            .build();

    when(enmeshedClientMock.searchRelationships(eq(relationshipTemplateId), any(), any()))
        .thenReturn(
            ResultWrapper.containing(
                List.of(
                    Relationship.builder()
                        .id(relationshipId)
                        .template(RelationshipTemplate.builder().id(relationshipTemplateId).build())
                        .peerIdentity(IdentityInfo.builder().address(userAddress).build())
                        .peer(userAddress)
                        .changes(
                            List.of(
                                RelationshipChange.builder()
                                    .id(relationshipChangeId)
                                    .type(RelationshipChange.Type.CREATION)
                                    .status(RelationshipChange.Status.PENDING)
                                    .request(relationshipChangeRequest)
                                    .build()))
                        .build())))
        .thenReturn(
            ResultWrapper.containing(
                List.of(
                    Relationship.builder()
                        .id(relationshipId)
                        .template(RelationshipTemplate.builder().id(relationshipTemplateId).build())
                        .peerIdentity(IdentityInfo.builder().address(userAddress).build())
                        .peer(userAddress)
                        .changes(
                            List.of(
                                RelationshipChange.builder()
                                    .id(relationshipChangeId)
                                    .type(RelationshipChange.Type.CREATION)
                                    .status(RelationshipChange.Status.REJECTED)
                                    .request(relationshipChangeRequest)
                                    .build()))
                        .build())));

    EnmeshedOnboardingService.RegistrationResult registrationResult =
        enmeshedService.checkRegistrationState(relationshipTemplateId, attributes -> false);

    Assertions.assertEquals(userAddress, registrationResult.enmeshedAddress());
    Assertions.assertEquals(relationshipId, registrationResult.relationshipId());
    Assertions.assertEquals(relationshipChangeId, registrationResult.relationshipChangeId());
    Assertions.assertEquals(
        userGivenName,
        ((GivenName) registrationResult.attributes().get(GivenName.class)).getValue());
    Assertions.assertEquals(
        userSurname, ((Surname) registrationResult.attributes().get(Surname.class)).getValue());
    Assertions.assertFalse(registrationResult.accepted());

    InOrder inOrder = Mockito.inOrder(enmeshedClientMock);
    inOrder.verify(enmeshedClientMock).sync();
    inOrder
        .verify(enmeshedClientMock)
        .searchRelationships(eq(relationshipTemplateId), any(), any());
    inOrder
        .verify(enmeshedClientMock)
        .rejectRelationshipChange(eq(relationshipId), eq(relationshipChangeId), any());
    inOrder.verify(enmeshedClientMock).sync();
    inOrder
        .verify(enmeshedClientMock)
        .searchRelationships(eq(relationshipTemplateId), any(), any());
  }

  @Test
  void itShouldPassTheSentAttributesToAcceptanceDecider() {

    String relationshipTemplateId = "RLT_XXX";
    String relationshipChangeId = "RCH_XXX";
    String relationshipId = "REL_XXX";
    String userGivenName = "Max";
    String userSurname = "Muster";
    String userAddress = "ADDR_XXX";
    enmeshedService = getServiceInstance();

    RelationshipChangeRequest relationshipChangeRequest =
        RelationshipChangeRequest.builder()
            .content(
                RelationshipCreationChangeRequestContent.builder()
                    .response(
                        Response.builder()
                            .items(
                                List.of(
                                    ReadAttributeAcceptResponseItem.builder()
                                        .result(ResponseItem.Result.ACCEPTED)
                                        .attribute(
                                            IdentityAttribute.builder()
                                                .value(
                                                    GivenName.builder()
                                                        .value(userGivenName)
                                                        .build())
                                                .build())
                                        .build(),
                                    ReadAttributeAcceptResponseItem.builder()
                                        .result(ResponseItem.Result.ACCEPTED)
                                        .attribute(
                                            IdentityAttribute.builder()
                                                .value(Surname.builder().value(userSurname).build())
                                                .build())
                                        .build()))
                            .requestId("REQ_ID")
                            .result(Response.Result.ACCEPTED)
                            .build())
                    .build())
            .build();

    when(enmeshedClientMock.searchRelationships(eq(relationshipTemplateId), any(), any()))
        .thenReturn(
            ResultWrapper.containing(
                List.of(
                    Relationship.builder()
                        .id(relationshipId)
                        .template(RelationshipTemplate.builder().id(relationshipTemplateId).build())
                        .peerIdentity(IdentityInfo.builder().address(userAddress).build())
                        .peer(userAddress)
                        .changes(
                            List.of(
                                RelationshipChange.builder()
                                    .id(relationshipChangeId)
                                    .type(RelationshipChange.Type.CREATION)
                                    .status(RelationshipChange.Status.PENDING)
                                    .request(relationshipChangeRequest)
                                    .build()))
                        .build())))
        .thenReturn(
            ResultWrapper.containing(
                List.of(
                    Relationship.builder()
                        .id(relationshipId)
                        .template(RelationshipTemplate.builder().id(relationshipTemplateId).build())
                        .peerIdentity(IdentityInfo.builder().address(userAddress).build())
                        .peer(userAddress)
                        .changes(
                            List.of(
                                RelationshipChange.builder()
                                    .id(relationshipChangeId)
                                    .type(RelationshipChange.Type.CREATION)
                                    .status(RelationshipChange.Status.ACCEPTED)
                                    .request(relationshipChangeRequest)
                                    .build()))
                        .build())));

    enmeshedService.checkRegistrationState(
        relationshipTemplateId,
        attributes -> {
          Assertions.assertEquals(
              userGivenName, ((GivenName) attributes.get(GivenName.class)).getValue());
          Assertions.assertEquals(
              userSurname, ((Surname) attributes.get(Surname.class)).getValue());
          Assertions.assertEquals(2, attributes.size());

          return true;
        });
  }

  private EnmeshedOnboardingService getServiceInstance() {

    when(enmeshedClientMock.getIdentityInfo())
        .thenReturn(ResultWrapper.containing(TEST_IDENTITY_INFO));

    AttributeWrapper wrappedDisplayNameAttribute =
        AttributeWrapper.builder()
            .id("ATTR_ID")
            .content(
                IdentityAttribute.builder()
                    .owner(TEST_IDENTITY_INFO.getAddress())
                    .value(DisplayName.builder().value(CONNECTOR_DISPLAY_NAME).build())
                    .build())
            .build();

    when(enmeshedClientMock.searchAttributes(anyString(), anyString(), anyString()))
        .thenReturn(ResultWrapper.containing(List.of(wrappedDisplayNameAttribute)));

    return new EnmeshedOnboardingService(
        enmeshedClientMock,
        CONNECTOR_DISPLAY_NAME,
        REQUIRED_ATTRIBUTES,
        OPTIONAL_ATTRIBUTES,
        CREATE_ATTRIBUTES);
  }
}
