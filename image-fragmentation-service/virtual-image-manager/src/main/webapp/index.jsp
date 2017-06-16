<%@page import="hu.mta.sztaki.lpds.entice.virtualimagemanager.rest.*,hu.mta.sztaki.lpds.entice.virtualimagemanager.database.*,java.io.*,java.util.*,javax.persistence.*" %>
<%@taglib uri="http://java.sun.com/jsp/jstl/sql" prefix="sql" %>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>

<html><head><title>ENTICE Virtual Image Manager Service</title></head>
<style>
	body {
		font-family: "Arial"
	}
	table {
		border: 0px solid black; 
		min-width: 700px;
		border-collapse: collapse;
	}
	td {
		border:1px solid gray; 
	}
</style>
<body>
<h2>ENTICE Virtual Image Manager Service</h2>
<h3>version: <%=Configuration.version%></h3>

<h4>Configuration:</h4>
<table>
	<tr><th>Property name</th><th>Value</th></tr>
	<tr><td>virtualImageDecomposerRestURL</td><td><%=Configuration.virtualImageDecomposerRestURL%></td></tr>
	<tr><td>installerStorageURL</td><td><%=Configuration.installerStorageURL%></td></tr>
	
</table>

<%
int numberOfBaseImages = 0;
int numberOfVirtualImages = 0;
int numberOfFragments = 0;
long sizeOfBaseImages = 0l;
long sizeOfVirtualImages = 0l;
long sizeOfFragments = 0;
%>

<%!
public static String humanReadableByteCount(long bytes, boolean si) {
    int unit = si ? 1000 : 1024;
    if (bytes < unit) return bytes + " B";
    int exp = (int) (Math.log(bytes) / Math.log(unit));
    String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp-1) + (si ? "" : "i");
    return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
}
%>

<h4>Base Images:</h4>
<%
		List<Image> baseList = new Vector<Image>();
        try {
			EntityManager entityManager = DBManager.getInstance().getEntityManager();
			entityManager.getTransaction().begin();
	        Query query = entityManager.createQuery("SELECT i FROM Image as i WHERE i.type = :type");
	        query.setParameter("type", Image.ImageType.BASE);
	        baseList = (List<Image>) query.getResultList();
			entityManager.getTransaction().commit();
			entityManager.close();
        } catch (Throwable x) {
        }
%>
<table>
<tr>
	    <th style="min-width:320px">Image id</th>
	    <th style="min-width:130px">Created</th>
	    <th>Name</th>
	    <th>Tags</th>
	    <th>Base image URL</th>
	    <th>Owner</th>
	    <th>Description</th>
	    <th>Partition</th>
	    <th>Cloud image ids</th>
	    <th>Size</th>
</tr>
<%
for (Image image: baseList) {
	numberOfBaseImages++;
	if (image.getImageSize() != null) sizeOfBaseImages += image.getImageSize();
%>
<tr>
		<jsp:useBean id="created" class="java.util.Date" />
		<jsp:setProperty name="created" property="time" value="<%=image.getCreated()%>" />	
	    <td><a href="rest/virtualimages/<%=image.getId()%>"><%=image.getId()%></a></td>
		<td><fmt:formatDate type="both" value="${created}" pattern="yyyy.MM.dd HH:mm" /></td>
	    <td><%=image.getName()%></td>
	    <td><%=image.getTags()%></td>
	    <td><a href="<%=image.getUrl()%>"><%=image.getUrl()%></a></td>
	    <td><%=image.getOwner()%></td>
	    <td><%=image.getDescription()%></td>
	    <td><%=image.getDiskPartition()%></td>
	    <td><%=image.getImageIds()%></td>
	    <td><%=image.getImageSize()%></td>
	</tr>
<%
} // for each image 
%>
</table>

<h4>Virtual Images:</h4>
<%
	List <Image> virtualList = new Vector<Image>();
        try {
			EntityManager entityManager = DBManager.getInstance().getEntityManager();
			entityManager.getTransaction().begin();
	        Query query = entityManager.createQuery("SELECT i FROM Image as i WHERE i.type = :type");
	        query.setParameter("type", Image.ImageType.VIRTUAL);
	        virtualList = (List<Image>) query.getResultList();
			entityManager.getTransaction().commit();
			entityManager.close();
        } catch (Throwable x) {
        }
%>
<table>
	<tr>
	    <th style="min-width:320px">Image id</th>
	    <th style="min-width:130px">Created</th>
	    <th>Name</th>
	    <th>Status</th>
	    <th>Message</th>
	    <th>Parent</th>
	    <th>Tags</th>
	    <th>Owner</th>
	    <th>Description</th>
	    <th>Size</th>
	    <th>Delta script</th>
	</tr>
<%
for (Image image: virtualList) {
	numberOfVirtualImages++;
	if (image.getImageSize() != null) sizeOfVirtualImages += image.getImageSize();

	List<String> aggregatedTags = VirtualImages.aggregateTags(image, new ArrayList<String>());
%>
	<tr>
		<jsp:setProperty name="created" property="time" value="<%=image.getCreated()%>" />	
	    <td><a href="rest/virtualimages/<%=image.getId()%>"><%=image.getId()%></a></td>
		<td><fmt:formatDate type="both" value="${created}" pattern="yyyy.MM.dd HH:mm" /></td>
	    <td><%=image.getName()%></td>
	    <td><%=image.getStatus()%></td>
	    <td><%=image.getMessage()%></td>
	    <td><a href="rest/virtualimages/<%=image.getIncomingEdges().get(0).getFromImage().getId()%>"><%=image.getIncomingEdges().get(0).getFromImage().getId()%></a></td>
	    <td><%=aggregatedTags.toString()%></td>
	    <td><%=image.getOwner()%></td>
	    <td><%=image.getDescription()%></td>
	    <td><%=image.getImageSize()%></td>
	    <td><a href="../virtual-image-composer/rest/scripts/<%=image.getId()%>">script</a></td>
	</tr>
<%
} // for each image 
%>
</table>

<h4>Edges:</h4>
<%
	List <Edge> edgeList = new Vector<Edge>();
        try {
			EntityManager entityManager = DBManager.getInstance().getEntityManager();
			entityManager.getTransaction().begin();
	        Query query = entityManager.createQuery("SELECT i FROM Edge as i");
	        edgeList = (List<Edge>) query.getResultList();
			entityManager.getTransaction().commit();
			entityManager.close();
        } catch (Throwable x) {
        }
%>
<table>
	<tr>
	    <th style="min-width:320px">To image</th>
	    <th>Parent image</th>
	    <th>Edge id</th>
	    <th>Installers</th>
	    <th>Installer tags</th>
	    <th>Fragment URL</th>
	    <th>Snapshot URL</th>
	    <th>Task id</th>
	    <th>Fragment size</th>
	</tr>
<%
for (Edge edge: edgeList) {
	numberOfFragments++;
	if (edge.getFragmentSize() != null) sizeOfFragments += edge.getFragmentSize();
%>
	<tr>
		<td><a href="rest/virtualimages/<%=edge.getToImage().getId()%>"><%=edge.getToImage().getId()%></a></td>
		<td><a href="rest/virtualimages/<%=edge.getFromImage().getId()%>"><%=edge.getFromImage().getId()%></a></td>
	    <td><%=edge.getId()%></td>
	    <td><%=edge.getInstallerIds()%></td>
	    <td><%=edge.getTags()%></td>
	    <td><a href="<%=edge.getFragmentUrl()%>"><%=edge.getFragmentUrl()!=null?edge.getFragmentUrl():""%></a></td>
	    <td><a href="<%=edge.getSnapshotUrl()%>"><%=edge.getSnapshotUrl()!=null?edge.getSnapshotUrl():""%></a></td>
	    <td><%=edge.getFragmentComputationTaskId()!=null?edge.getFragmentComputationTaskId():""%></td>
	    <td><%=edge.getFragmentSize()%></td>
	</tr>
<%
} // for each edge
%>
</table>

<h4>Storage statistics:</h4>

<table>
<tr>
	    <th></th>
	    <th>Number</th>
	    <th>Size</th>
</tr>
<tr>
	<td>Base images</td>
	<td><%=numberOfBaseImages%></td>
	<td><%=humanReadableByteCount(sizeOfBaseImages, true)%></td>
</tr>
<tr>
	<td>Virtual images</td>
	<td><%=numberOfVirtualImages%></td>
	<td><%=humanReadableByteCount(sizeOfVirtualImages, true)%></td>
</tr>
<tr>
	<td>Fragments</td>
	<td><%=numberOfFragments%></td>
	<td><%=humanReadableByteCount(sizeOfFragments, true)%></td>
</tr>
</table>

<br/>
Storage space reduction (sizeof(base images + fragments) / sizeof(base images + virtual images)): <%=humanReadableByteCount(sizeOfBaseImages + sizeOfFragments, true)%> / <%=humanReadableByteCount(sizeOfBaseImages + sizeOfVirtualImages, true)%><br/>
Reduction ratio (1 - virtual size/real size): <%=sizeOfBaseImages==0l?"-":String.format("%.1f", 100 - 100*((double)sizeOfBaseImages + (double)sizeOfFragments)/((double)sizeOfBaseImages + (double)sizeOfVirtualImages))%>% 

</body></html>