package com.getman.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** An editable HTTP request, as shown in one editor tab and saved to disk. */
public final class RequestModel implements CollectionNode {
	private String id = UUID.randomUUID().toString();
	private String name = "New Request";
	private HttpMethod method = HttpMethod.GET;
	private String url = "";
	private final List<KeyValue> params = new ArrayList<>();
	private final List<KeyValue> headers = new ArrayList<>();
	private BodyType bodyType = BodyType.NONE;
	private String body = "";
	private final Auth auth = new Auth();
	private final List<FormField> formFields = new ArrayList<>();
	private String binaryFile = "";
	/** Id of the collection this request was opened from or saved to; empty when it is in none. */
	private String collectionId = "";

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name == null ? "" : name;
	}

	public HttpMethod getMethod() {
		return method;
	}

	public void setMethod(HttpMethod method) {
		this.method = method == null ? HttpMethod.GET : method;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url == null ? "" : url;
	}

	/** Live, mutable list of query parameters. */
	public List<KeyValue> getParams() {
		return params;
	}

	/** Live, mutable list of request headers. */
	public List<KeyValue> getHeaders() {
		return headers;
	}

	public BodyType getBodyType() {
		return bodyType;
	}

	public void setBodyType(BodyType bodyType) {
		this.bodyType = bodyType == null ? BodyType.NONE : bodyType;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body == null ? "" : body;
	}

	public String getCollectionId() {
		return collectionId;
	}

	public void setCollectionId(String collectionId) {
		this.collectionId = collectionId == null ? "" : collectionId;
	}

	public boolean isInCollection() {
		return !collectionId.isEmpty();
	}

	/** Live, mutable authorization settings. */
	public Auth getAuth() {
		return auth;
	}

	/**
	 * Value of the Authorization header the auth settings add, or {@code null} when they add none: no auth, empty
	 * fields, or the user set an active {@code Authorization} header explicitly (which always wins).
	 */
	public String authorizationFromAuth() {
		return hasActiveHeader(Auth.HEADER) ? null : auth.headerValue();
	}

	/** Live, mutable list of multipart form fields (used when the body type is {@link BodyType#MULTIPART}). */
	public List<FormField> getFormFields() {
		return formFields;
	}

	/** Path of the file sent as the body when the body type is {@link BodyType#BINARY}. */
	public String getBinaryFile() {
		return binaryFile;
	}

	public void setBinaryFile(String binaryFile) {
		this.binaryFile = binaryFile == null ? "" : binaryFile;
	}

	/** Whether a body will actually be sent. */
	public boolean hasBody() {
		return switch (bodyType) {
		case NONE -> false;
		case MULTIPART -> formFields.stream().anyMatch(FormField::isActive);
		case BINARY -> !binaryFile.isBlank();
		default -> !body.isEmpty();
		};
	}

	/** Case-insensitive lookup of an active header the user set explicitly. */
	public boolean hasActiveHeader(String headerName) {
		return headers.stream().anyMatch(h -> h.isActive() && h.getKey().trim().equalsIgnoreCase(headerName));
	}

	/** Deep copy that keeps the id and the collection link. */
	public RequestModel copy() {
		RequestModel copy = duplicate();
		copy.id = id;
		copy.name = name;
		copy.collectionId = collectionId;
		return copy;
	}

	/** Whether the user-editable fields (everything except id and collection link) are equal. */
	public boolean sameContent(RequestModel other) {
		return other != null && name.equals(other.name) && method == other.method && url.equals(other.url)
				&& params.equals(other.params) && headers.equals(other.headers) && bodyType == other.bodyType
				&& body.equals(other.body) && auth.equals(other.auth) && formFields.equals(other.formFields)
				&& binaryFile.equals(other.binaryFile);
	}

	/** Deep copy with a fresh id and no collection link, used for "Duplicate". */
	public RequestModel duplicate() {
		RequestModel copy = new RequestModel();
		copy.name = name + " (copy)";
		copy.method = method;
		copy.url = url;
		params.forEach(p -> copy.params.add(p.copy()));
		headers.forEach(h -> copy.headers.add(h.copy()));
		copy.bodyType = bodyType;
		copy.body = body;
		copy.auth.setType(auth.getType());
		copy.auth.setToken(auth.getToken());
		copy.auth.setUsername(auth.getUsername());
		copy.auth.setPassword(auth.getPassword());
		formFields.forEach(f -> copy.formFields.add(f.copy()));
		copy.binaryFile = binaryFile;
		return copy;
	}
}
