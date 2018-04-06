# FT Alphaville Long Room Scraper
## TODO
* Add command line options (especially session ID and dev token)
* Change to use Akka actors?
## Possible Actor Design
(how to shut down?)
### Non-actor components and actions
* Evernote API (EV)
* FT Scraper API (FT)
* Get all notes from EV
* LinkScraper, callback for each set of links found
  * notify Article Scraper (see actors)
  * when done, notify Progress Drawer (see actors)
### Actor components
* ArticleScraper
  * include attachment or not?
  * notify Note Persister
* NotePersister
  * create & persist note
  * include inline attachment?
  * if save attachment, create File and notify AttachmentPersister
  * must be rate limited
  * once persisted, notify Progress Drawer
* AttachmentPersister
  * save to file system
* ProgressDrawer
  * on notify from NotePersister: Change from collecting to drawing
  * on notify from LinkScraper
    * when collecting: increment internal counter
    * when drawing: update and draw
    
