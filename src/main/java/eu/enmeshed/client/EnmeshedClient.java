package eu.enmeshed.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import eu.enmeshed.annotation.Retryable;
import eu.enmeshed.exception.decoder.EnmeshedErrorDecoder;
import eu.enmeshed.model.AttributeWrapper;
import eu.enmeshed.model.ContentWrapper;
import eu.enmeshed.model.IdentityInfo;
import eu.enmeshed.model.ResultWrapper;
import eu.enmeshed.model.attributes.Attribute;
import eu.enmeshed.model.file.FileMetaData;
import eu.enmeshed.model.file.FileReference;
import eu.enmeshed.model.file.FileUploadRequest;
import eu.enmeshed.model.messaging.Message;
import eu.enmeshed.model.messaging.SendMessage;
import eu.enmeshed.model.qr.QrCode;
import eu.enmeshed.model.relationshipTemplates.RelationshipTemplate;
import eu.enmeshed.model.relationshipTemplates.RelationshipTemplateCreation;
import eu.enmeshed.model.relationships.Relationship;
import eu.enmeshed.model.request.LocalRequest;
import eu.enmeshed.retryer.CustomRetryer;
import feign.Feign;
import feign.Headers;
import feign.Logger;
import feign.Param;
import feign.QueryMap;
import feign.Request;
import feign.RequestLine;
import feign.Response;
import feign.form.FormEncoder;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import java.util.List;

public interface EnmeshedClient {

  ObjectMapper objectMapper =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .setSerializationInclusion(JsonInclude.Include.NON_ABSENT)
          .disable(SerializationFeature.INDENT_OUTPUT)
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .setDateFormat(new StdDateFormat().withColonInTimeZone(true));

  static EnmeshedClient configure(String url, String apiKey) {

    return configure(url, apiKey, new Request.Options(), Logger.Level.NONE);
  }

  static EnmeshedClient configure(
      String url, String apiKey, Request.Options options, Logger.Level loggerLevel) {

    return Feign.builder()
        .decoder(new JacksonDecoder(objectMapper))
        .encoder(new FormEncoder(new JacksonEncoder(objectMapper)))
        .requestInterceptor(request -> request.header("X-API-KEY", apiKey))
        .logLevel(loggerLevel)
        .options(options)
        .errorDecoder(new EnmeshedErrorDecoder())
        .retryer(new Retryable.AnnotationRetryer(new CustomRetryer()))
        .target(EnmeshedClient.class, url);
  }

  /*
   Account
  */
  @RequestLine("GET /api/v2/Account/IdentityInfo")
  ResultWrapper<IdentityInfo> getIdentityInfo();

  @RequestLine("POST /api/v2/Account/Sync")
  void sync();

  /*
   Attributes
  */
  @RequestLine("GET /api/v2/Attributes?content.@type={0}&content.owner={1}&content.value.@type={2}")
  ResultWrapper<List<AttributeWrapper>> searchAttributes(
      @Param("0") String contentType,
      @Param("1") String contentOwner,
      @Param("2") String contentValueType);

  @RequestLine("POST /api/v2/Attributes")
  @Headers("Content-Type: application/json")
  ResultWrapper<AttributeWrapper> createAttribute(ContentWrapper<Attribute> attribute);

  /*
   Relationship Templates
  */
  @RequestLine("POST /api/v2/RelationshipTemplates/Own")
  @Headers("Content-Type: application/json")
  ResultWrapper<RelationshipTemplate> createOwnRelationshipTemplate(
      RelationshipTemplateCreation relationshipTemplate);

  @RequestLine("GET /api/v2/RelationshipTemplates/{0}")
  @Headers("Accept: image/png")
  Response getQrCodeForRelationshipTemplate(@Param("0") String relationshipTemplateId);

  @RequestLine("GET /api/v2/RelationshipTemplates/{relationshipTemplateId}")
  @Headers("Accept: application/json")
  ResultWrapper<QrCode> createRelationshipQrCode(
      @Param("relationshipTemplateId") String relationshipTemplateId);

  /*
   Relationships
  */
  @RequestLine("GET /api/v2/Relationships?template.id={0}&peer={1}&status={2}")
  ResultWrapper<List<Relationship>> searchRelationships(
      @Param("0") String templateId, @Param("1") String peer, @Param("2") String status);

  @RequestLine("PUT /api/v2/Relationships/{0}/Changes/{1}/Accept")
  @Headers("Content-Type: application/json")
  ResultWrapper<Relationship> acceptRelationshipChange(
      @Param("0") String relationshipId,
      @Param("1") String changeId,
      ContentWrapper<Object> dummyBody);

  @RequestLine("PUT /api/v2/Relationships/{0}/Changes/{1}/Reject")
  @Headers("Content-Type: application/json")
  ResultWrapper<Relationship> rejectRelationshipChange(
      @Param("0") String relationshipId,
      @Param("1") String changeId,
      ContentWrapper<Object> dummyBody);

  /*
   Messages
  */
  @Retryable
  @RequestLine("POST /api/v2/Messages")
  @Headers("Content-Type: application/json")
  ResultWrapper<Message> sendMessage(SendMessage message);

  @RequestLine("GET /api/v2/Messages")
  ResultWrapper<List<Message>> searchMessages(@QueryMap MessageSearchQuery searchQuery);

  @RequestLine("GET /api/v2/Messages/{0}")
  ResultWrapper<Message> getMessageById(@Param("0") String id);

  /*
   Requests
  */
  @RequestLine("POST /api/v2/Requests/Outgoing")
  @Headers("Content-Type: application/json")
  ResultWrapper<LocalRequest> createOutgoingRequest(LocalRequest request);

  @RequestLine("GET /api/v2/Requests/Outgoing/{0}")
  ResultWrapper<LocalRequest> getOutgoingRequest(@Param("0") String requestId);

  @Retryable
  @RequestLine("GET /api/v2/Requests/Incoming/{requestId}")
  @Headers("Content-Type: application/json")
  ResultWrapper<LocalRequest> getIncomingRequestById(@Param("requestId") String requestId);

  @Retryable
  @RequestLine("PUT /api/v2/Requests/Incoming/{requestId}/Accept")
  @Headers("Content-Type: application/json")
  ResultWrapper<LocalRequest> acceptIncomingRequestById(
      @Param("requestId") String requestId, eu.enmeshed.model.request.Request request);

  /*
  Files
   */
  @Retryable
  @RequestLine("POST /api/v2/Files/Own")
  @Headers({"Content-Type:  multipart/form-data", "accept: application/json"})
  ResultWrapper<FileMetaData> uploadNewOwnFile(FileUploadRequest fileUploadRequest);

  @Retryable
  @RequestLine("GET /api/v2/Files/{fileId}/Download")
  @Headers("Accept: application/json")
  Response getFileResponseById(@Param("fileId") String fileId);

  @Retryable
  @RequestLine("GET /api/v2/Files/{fileId}")
  @Headers("Accept: application/json")
  ResultWrapper<FileMetaData> getFileMetadataByFileId(@Param("fileId") String fileId);

  @Retryable
  @RequestLine("POST /api/v2/Files/Peer")
  @Headers("Content-Type: application/json")
  ResultWrapper<FileMetaData> getFileMetadataByReference(FileReference reference);
}
