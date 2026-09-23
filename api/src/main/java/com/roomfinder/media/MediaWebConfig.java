package com.roomfinder.media;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Serves locally stored media over HTTP. Dev-only concern; an R2-backed storage needs no such mapping. */
@Configuration
class MediaWebConfig implements WebMvcConfigurer {

	private final LocalDiskMediaStorage storage;

	MediaWebConfig(LocalDiskMediaStorage storage) {
		this.storage = storage;
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/media/**")
				.addResourceLocations("file:" + storage.root() + "/");
	}
}
