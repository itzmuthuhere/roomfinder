package com.roomfinder.media;

/**
 * Where uploaded photo/video bytes live. Local disk in dev; swap in an R2-backed
 * implementation later without touching {@link MediaService}.
 */
interface MediaStorage {

	/** Writes content under a server-generated key and returns that key. */
	String store(byte[] content, String extension);

	/** URL the frontend can load the file from. */
	String publicUrl(String key);

	/** Best-effort delete. Callers must only invoke this after the owning DB transaction has committed. */
	void delete(String key);
}
