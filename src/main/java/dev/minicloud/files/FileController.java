package dev.minicloud.files;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The owner always comes from the authenticated principal, never from the request.
 */
@Controller
class FileController {

    private final FileService fileService;

    FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping("/")
    String home() {
        return "redirect:/files";
    }

    @GetMapping("/files")
    String list(Authentication auth, Model model) throws IOException {
        model.addAttribute("username", auth.getName());
        model.addAttribute("files", fileService.list(auth.getName()));
        return "files";
    }

    @PostMapping("/files")
    String upload(Authentication auth, @RequestParam(name = "file", required = false) MultipartFile file,
                  RedirectAttributes redirect) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new UploadRejectedException(UploadRejectedException.Reason.EMPTY);
        }
        try (InputStream in = file.getInputStream()) {
            fileService.upload(auth.getName(), file.getOriginalFilename(), file.getSize(), in);
        }
        redirect.addFlashAttribute("message", "Archivo subido.");
        return "redirect:/files";
    }

    @GetMapping("/files/{id}/download")
    ResponseEntity<Resource> download(Authentication auth, @PathVariable String id) throws IOException {
        return DownloadResponses.attachment(fileService.open(auth.getName(), id));
    }

    @PostMapping("/files/{id}/delete")
    String delete(Authentication auth, @PathVariable String id, RedirectAttributes redirect) throws IOException {
        fileService.delete(auth.getName(), id);
        redirect.addFlashAttribute("message", "Archivo eliminado.");
        return "redirect:/files";
    }
}
