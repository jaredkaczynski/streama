package streama

import grails.converters.JSON
import static javax.servlet.http.HttpServletResponse.SC_NOT_ACCEPTABLE
import grails.transaction.Transactional

import static org.springframework.http.HttpStatus.*

@Transactional
class FileService {

  def allowedVideoFormats = ['.mp4', '.mkv', '.webm', '.ogg', '.m4v']

  def serveVideo(request, response, rawFile, File file, String filePath) {
    def rangeHeader = request.getHeader("Range")
    //bytes=391694320-

    System.out.println(filePath)

    def fileLength = rawFile.length()
    def contentLength = rawFile.length().toString()
    def rangeEnd = fileLength.toLong() - 1
    def rangeStart

    if (rangeHeader) {
      String[] range = rangeHeader.substring(6).split("-")
      rangeStart = range[0].toLong()
      if (range.length == 2)
        rangeEnd = range[1].toLong()

      contentLength = rangeEnd + 1 - rangeStart
    }
    //add html5 video headers
    response.addHeader("Accept-Ranges", "bytes")
    response.addHeader("Content-Length", contentLength.toString())
    response.addHeader("Last-Modified", (new Date()).toString())
    response.addHeader("Cache-Control", 'public,max-age=3600,public')
    response.addHeader("Etag", file.sha256Hex)

    System.out.println("Headers Added")


    if (rangeHeader) {
      response.addHeader("Content-Range", "bytes $rangeStart-$rangeEnd/$fileLength")
      response.setStatus(PARTIAL_CONTENT.value())
    }

    System.out.println("Range Header Set")

    InputStream fis

    System.out.println("Above Process Builder")

    byte[] buffer = new byte[16000]

    int skip = 0
    if (rangeStart) {
      skip = rangeStart
    }

    ProcessBuilder pb = new ProcessBuilder("ffmpeg"
      , "-skip_initial_bytes", skip.toString()
      , "-re"
      , "-i", "\"" + filePath + "\""
      , "-vcodec", "libx264"
      , "-preset", "veryfast"
      , "-crf", "30"
      , "-acodec", "aac"
      , "-f", "mpegts"
      , "-")
    Process p = pb.start()
    System.out.println("Process Builder started")

    fis = p.getInputStream()

    System.out.println("Input Stream Grabbed")

    BufferedReader reader =
      new BufferedReader(new InputStreamReader(p.getErrorStream()))
    try {
      while (true) {
        String line = null
        while ( (line = reader.readLine()) != null) {
          System.out.println(line)
        }
        int read = fis.read(buffer)
        if (read == -1) {
          break
        }
        response.outputStream.write(buffer, 0, read)
      }
    } catch (Exception e) {
//      log.error('caught exception for video playback. ' + e.message)
//      e.printStackTrace()
//      e.getCause().printStackTrace()
    } finally {
//      response.outputStream.flush()
      fis.close()
    }
  }


  def Map fullyRemoveFile(File file) {
    if (!file) {
      return ResultHelper.generateErrorResult(SC_NOT_ACCEPTABLE, 'file', 'No valid file selected.')
    }
    if (file.localFile) {
      return ResultHelper.generateErrorResult(SC_NOT_ACCEPTABLE, 'local', 'cant delete file associated with the File-Browser.')
    }
    if (file.externalLink) {
      return ResultHelper.generateErrorResult(SC_NOT_ACCEPTABLE, 'external', 'cant delete file associated with an external Link.')
    }
    if (file.associatedVideosInclDeleted) {
      file.associatedVideosInclDeleted.each { video ->
        video.removeFromFiles(file)
        video.save(flush: true, failOnError: true)
      }
    }

    if (file.isInUse) {
      def tvShowByPoster = TvShow.findByPoster_image(file)
      if (tvShowByPoster) {
        tvShowByPoster.poster_image = null
        tvShowByPoster.save(flush: true, failOnError: true)
      }

      def tvShowByBackdrop = TvShow.findByPoster_image(file)
      if (tvShowByBackdrop) {
        tvShowByBackdrop.backdrop_image = null
        tvShowByBackdrop.save(flush: true, failOnError: true)
      }
    }

    if (file.imagePath && file.fileExists) {
      java.io.File rawFile = new java.io.File(file.imagePath)
      rawFile.delete()
    }

    file.delete(flush: true)


    return ResultHelper.generateOkResult()
  }
}
