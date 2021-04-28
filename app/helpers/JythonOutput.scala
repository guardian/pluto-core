package helpers

/**
  * this case class represents the result of the invokation of a script in Jython. It's kept even though
  * Jython is deprecated because it's the common format used to store and forward the results of a postrun
  * @param stdOutContents what the script put to stdout
  * @param stdErrContents what the script put to stderr
  * @param newDataCache updated data cache object containing any key-values output by this script
  * @param raisedError either None, if the run completed successfully, or a Throwable representing an error that occurred
  */
case class JythonOutput(stdOutContents: String, stdErrContents: String, newDataCache:PostrunDataCache, raisedError: Option[Throwable])