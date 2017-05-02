<%@page import="hu.mta.sztaki.lpds.entice.installerstorage.rest.*,java.io.*,org.json.*" %>
<%@taglib uri="http://java.sun.com/jsp/jstl/sql" prefix="sql" %>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>

<html><head><title>ENTICE Installer Storage Service</title></head>
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
<h2>ENTICE Installer Storage  Service</h2>
<h3>version: <%=Configuration.version%></h3>

<h4>Configuration:</h4>
<table>
	<tr><th>Property name</th><th>Value</th></tr>
	<tr><td>installerStoragePath</td><td><%=Configuration.installerStoragePath%></td></tr>
</table>

<h4>Installers:</h4>
<table>
<tr>
	    <th>Name</th>
	    <th>Metadata</th>
	    <th>Installer script</th>
	    <th>Init script</th>
</tr>
<%
	for (String key: Installers.installerMetadata.keySet()) {
		JSONObject o = Installers.installerMetadata.get(key);
%>
<tr>
	    <td><%=key%></td>
	    <td><%=o.toString()%></td>
	    <td><a href="rest/installers/<%=key%>/install">install</a></td>
	    <td><a href="rest/installers/<%=key%>/init">init</a></td>
	</tr>
<%
} 
%>
</table>

</body></html>