package eu.enmeshed.model.relationships;

import com.fasterxml.jackson.annotation.JsonProperty;
import eu.enmeshed.model.IdentityInfo;
import eu.enmeshed.model.event.WebhookData;
import eu.enmeshed.model.relationshipTemplates.RelationshipTemplate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class Relationship implements WebhookData {

  private String id;

  private RelationshipTemplate template;

  private Status status;

  private String peer;

  private IdentityInfo peerIdentity;

  private List<RelationshipChange> changes;

  public enum Status {
    @JsonProperty("Pending")
    PENDING,

    @JsonProperty("Active")
    ACTIVE,

    @JsonProperty("Rejected")
    REJECTED
  }
}
