package hu.mta.sztaki.lpds.entice.virtualimagemanager.database;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.MapKeyColumn;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;

// persistent class to store base and virtual images
@Entity @Table public class Image {
	
	@Transient public final static String ID = "id"; 
	@Transient public final static String NAME = "name"; 
	@Transient public final static String TYPE = "type"; 
	@Transient public final static String STATUS = "status";
	@Transient public final static String MESSAGE = "message";
	@Transient public final static String PARENT_VIRTUAL_IMAGE_ID = "parent";
	@Transient public final static String CREATED = "created";
	@Transient public final static String OWNER = "owner"; // required
	@Transient public final static String DESCRIPTION = "description"; // optional
	@Transient public final static String URL = "url"; // required for base images
	@Transient public final static String PARTITION = "partition"; // optional
	@Transient public final static String TAGS = "tags"; // optional
	@Transient public final static String CLOUD_IMAGE_IDS = "cloudImageIds"; // optional
	@Transient public final static String SOURCE_VIRTUAL_IMAGE_ID = "sourceVirtualImageId"; // required (build VI)
	@Transient public final static String TARGET_VIRTUAL_IMAGE_ID = "targetVirtualImageId"; // optional (build VI)
	@Transient public final static String SOURCE_BASE_IMAGE_URL = "sourceBaseImageUrl"; // required (build VI)

	@Transient public final static String FRAGMENT_IDS = "fragmentIds";
	@Transient public final static int MAX_NUMBER_OF_ACCUMULATED_TAGS = 1000; 

	// id
	@Id	private String id = UUID.randomUUID().toString(); // @GeneratedValue(generator="system-uuid") @GenericGenerator(name="system-uuid", strategy="uuid")
	public void setId(String id) { this.id = id; }
	public String getId() { return this.id; }

	// name
	private String name = ""; 
	public void setName(String name) { this.name = name; }
	public String getName() { return this.name; }
	
	// type
	public enum ImageType { BASE, VIRTUAL }
	@Enumerated(EnumType.STRING) ImageType type = ImageType.BASE;
	public ImageType getType() { return type; }
	public void setType(ImageType type) { this.type = type; }

	// status
	public enum ImageStatus { READY, PENDING, FAILED }
	@Enumerated(EnumType.STRING) ImageStatus status = ImageStatus.READY;
	public ImageStatus getStatus() { return status; }
	public void setStatus(ImageStatus status) { this.status = status; }

	// message (phase on PENDING, failure reason on FAILED)
	private String message = "";
	public void setMessage(String message) { this.message = message; }
	public String getMessage() { return this.message; }
	
	// date of creation
	private long created = System.currentTimeMillis();
	public void setCreated(long created) { this.created = created; }
	public long getCreated() { return this.created; }

	// author
	private String owner = "";
	public String getOwner() { return owner;}
	public void setOwner(String owner) { this.owner = owner; }

	// description
	@Lob private String description = "";
	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = description; }

	// download URL
	@Lob private String url = "";
	public String getUrl() { return url; }
	public void setUrl(String url) { this.url = url; }

	// partition number or logical volume (volume-group logical-volume-name) of the root file system
	private String diskPartition = "";
	public String getDiskPartition() { return diskPartition; }
	public void setDiskPartition(String partition) { this.diskPartition = partition; }
	
	// tags
	@ElementCollection(fetch=FetchType.EAGER) @CollectionTable(name="imageTags")
	private Set<String> tags = new HashSet<String>();
	public Set<String> getTags() { return tags; }
	public void setTags(Set<String> tags) { this.tags = tags; }
	public void addTag(String tag) { this.tags.add(tag); }
	
	// proprietary image ids in clouds
	@ElementCollection(fetch=FetchType.EAGER) @CollectionTable(name="imageIds")
	@MapKeyColumn(name="cloud") @Column(name="id")
	Map<String, String> imageIds = new HashMap<String, String>(); // maps from attribute name to value
	public Map<String, String> getImageIds() { return this.imageIds; }
	public void setImageIds(Map<String, String> map) { this.imageIds = map; }
	public void addImageId(String cloud, String id) { this.imageIds.put(cloud, id); }
	
	// outgoing edges (fromImage = this image)
	@OneToMany @JoinColumn(name="fromImage", referencedColumnName="id")
	List <Edge> outgoingEdges = new ArrayList<Edge>();
	public List<Edge> getOutgoingEdges() { return outgoingEdges; }
	public void setOutgoingEdges(List<Edge> edges) { this.outgoingEdges = edges; }
	public void addOutgoingEdge(Edge edge) { this.outgoingEdges.add(edge); }

	// incoming edges (toImage = this image)
	@OneToMany @JoinColumn(name="toImage", referencedColumnName="id")
	List <Edge> incomingEdges = new ArrayList<Edge>();
	public List<Edge> getIncomingEdges() { return incomingEdges; }
	public void setIncomingEdges(List<Edge> edges) { this.incomingEdges = edges; }
	public void addIncomingEdge(Edge edge) { this.incomingEdges.add(edge); }
}