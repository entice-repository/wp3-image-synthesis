package hu.mta.sztaki.lpds.entice.virtualimagemanager.database;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.persistence.CollectionTable;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;

//persistent class to store edges between base and virtual images
@Entity @Table public class Edge {
	
	@Transient public final static String ID = "id"; 
	@Transient public final static String TAGS = "tags";
	@Transient public final static String STATUS = "status";
	@Transient public final static String INSTALLER_IDS = "installerIds";
//	@Transient public final static String FRAGMENT_ID = "fragmentId";
	@Transient public final static String FRAGMENT_URL = "fragmentUrl";
	@Transient public final static String SNAPSHOT_URL = "snapshotUrl";

	@Transient public final static String INSTALLER_BASE64 = "installerBase64";
	@Transient public final static String INIT_BASE64 = "initBase64";	
	
	@Transient public final static long BUILD_TIMEOUT = 24 * 60 * 60l; // one day in seconds

	// id
	@Id	private String id = UUID.randomUUID().toString(); // @GeneratedValue(generator="system-uuid") @GenericGenerator(name="system-uuid", strategy="uuid")
	public void setId(String id) { this.id = id; }
	public String getId() { return this.id; }

	// date of creation
	private long created = System.currentTimeMillis();
	public void setCreated(long created) { this.created = created; }
	public long getCreated() { return this.created; }

	// from
	@ManyToOne @JoinColumn(name="fromImage") Image fromImage;
	public Image getFromImage() { return fromImage; }
	public void setFromImage(Image fromImage) { this.fromImage = fromImage; }

	// to
	@ManyToOne @JoinColumn(name="toImage") Image toImage;
	public Image getToImage() { return toImage; }
	public void setToImage(Image toImage) { this.toImage = toImage; }
	
	// status
	public enum EdgeStatus { READY, PENDING, FAILED }
	@Enumerated(EnumType.STRING) EdgeStatus status = EdgeStatus.READY;
	public EdgeStatus getStatus() { return status; }
	public void setStatus(EdgeStatus status) { this.status = status; }

	// installer ids
	@ElementCollection(fetch=FetchType.EAGER) @CollectionTable(name="edgeInstallers")
	private Set<String> installerIds = new HashSet<String>();
	public Set<String> getInstallerIds() { return installerIds; }
	public void setInstallerIds(Set<String> ids) { this.installerIds = ids; }
	public void addInstallerId(String id) { this.installerIds.add(id); }
	
	// installer implied tags
	@ElementCollection(fetch=FetchType.EAGER) @CollectionTable(name="edgeTags")
	private Set<String> tags = new HashSet<String>();
	public Set<String> getTags() { return tags; }
	public void setTags(Set<String> tags) { this.tags = tags; }

	// snapshot URL of the target image (manually produced)
	@Lob private String snapshotUrl = "";
	public String getSnapshotUrl() { return snapshotUrl; }
	public void setSnapshotUrl(String url) { this.snapshotUrl = url; }
	
	// fragment id when edge is READY
	@Lob private String fragmentUrl = "";
	public String getFragmentUrl() { return fragmentUrl; }
	public void setFragmentUrl(String fragmentUrl) { this.fragmentUrl = fragmentUrl; }	

	// fragment calculation id (returned by virtual image decomposer)
	@Lob private String fragmentComputationTaskId = "";
	public String getFragmentComputationTaskId() { return fragmentComputationTaskId; }
	public void setFragmentComputationTaskId(String fragmentComputationTaskId) { this.fragmentComputationTaskId = fragmentComputationTaskId; }	
}