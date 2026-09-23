package com.roomfinder.media;

import com.roomfinder.common.ApiException;
import com.roomfinder.media.MediaDtos.MediaView;
import com.roomfinder.media.MediaDtos.ReorderRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class MediaController {

	private final MediaService service;

	public MediaController(MediaService service) {
		this.service = service;
	}

	@GetMapping("/pgs/{pgId}/media")
	public List<MediaView> list(@PathVariable long pgId) {
		return service.listForPg(pgId);
	}

	@PostMapping("/pgs/{pgId}/rooms/{roomId}/media/photos")
	public MediaView uploadPhoto(@PathVariable long pgId, @PathVariable long roomId,
	                             @RequestParam("file") MultipartFile file) {
		return service.uploadPhoto(pgId, roomId, read(file));
	}

	@PostMapping("/pgs/{pgId}/media/video")
	public MediaView uploadVideo(@PathVariable long pgId, @RequestParam("file") MultipartFile file) {
		return service.uploadVideo(pgId, read(file));
	}

	@PatchMapping("/pgs/{pgId}/media/{mediaId}/cover")
	public MediaView setCover(@PathVariable long pgId, @PathVariable long mediaId) {
		return service.setCover(pgId, mediaId);
	}

	@PatchMapping("/pgs/{pgId}/rooms/{roomId}/media/reorder")
	public List<MediaView> reorder(@PathVariable long pgId, @PathVariable long roomId,
	                               @Valid @RequestBody ReorderRequest req) {
		service.reorder(pgId, roomId, req.orderedIds());
		return service.listForPg(pgId);
	}

	@DeleteMapping("/media/{mediaId}")
	public Map<String, Object> delete(@PathVariable long mediaId) {
		service.delete(mediaId);
		return Map.of("deleted", mediaId);
	}

	private byte[] read(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_MISSING", "No file was uploaded");
		}
		try {
			return file.getBytes();
		} catch (IOException e) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_UNREADABLE", "Could not read the uploaded file");
		}
	}
}
