package com.roomfinder.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Component
class LocalDiskMediaStorage implements MediaStorage {

	private final Path root;

	LocalDiskMediaStorage(@Value("${media.root}") String rootPath) {
		this.root = Path.of(rootPath).toAbsolutePath().normalize();
		try {
			Files.createDirectories(root);
		} catch (IOException e) {
			throw new UncheckedIOException("could not create media.root " + root, e);
		}
	}

	Path root() {
		return root;
	}

	@Override
	public String store(byte[] content, String extension) {
		String key = UUID.randomUUID() + "." + extension;
		try {
			Files.write(root.resolve(key), content);
		} catch (IOException e) {
			throw new UncheckedIOException("could not write media file " + key, e);
		}
		return key;
	}

	@Override
	public String publicUrl(String key) {
		return "/media/" + key;
	}

	@Override
	public void delete(String key) {
		try {
			Files.deleteIfExists(root.resolve(key));
		} catch (IOException e) {
			throw new UncheckedIOException("could not delete media file " + key, e);
		}
	}
}
