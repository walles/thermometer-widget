# TODO
* Add Crashlytics support

* On click, pop up yr.no's hour-by-hour prognosis and require the user
  to pop up a menu to access the preferences.

* Put high / low forecast temperature for the nearest 10 hours
  somewhere.  Maybe on the right half of a 2x1 format widget?

* Put best / worst weather for the nearest 10 hours somewhere.  Maybe
  on the right half of a 2x1 format widget?

* Write tests for Util.toLocal()


# DONE
* Pop up preferences when widget is clicked.

* Don't show preferences when adding widget, it's just confusing and
  slows down the add.

* Fix random crashes by starting a Service rather than using an
  AsyncTask for downloading weather observations:
http://stackoverflow.com/questions/1864153/android-appwidgets-alarmmanager-and-asynctask

* Don't reload temperature data when preferences change, just
  re-render the existing data.  Can we subscribe to preferences
  updates?

* Make widget display temperature for the current location, not just
  for outside my apartment.

* Add a preference for adjusting the shown temperature for wind chill.

* Show a timestamp and the name of the measurement point in small text
  below the actual temperature reading.

* Use the metadata field to display status messages about the
  temperature reading.  Except for showing where the current data is
  from, the following statuses should be supported:
  * Fetching... (only when starting up)
  * Network unavailable
  * Wether service error, will retry HH:MM
  * Location unavailable
  * Bad weather data received

* Make sure that any state we have is outside of the
  AppWidgetProvider.  In particular these references have to be stored
  somewhere else:
  * appWidgetIds
  * location
  * status
  * updateListener
  * weather
  On the real device with all its processes running, our
  AppWidgetProvider keeps getting killed off when others need the
  resources it occupies, and we need something else to keep state for
  us, something that won't be killed all the time.  A service seems
  like the right thing here, and we need a service that can keep all
  that data for us, and that won't just go away as soon as we aren't
  doing anything.

  Read up on services.

* Fix the on-click response so that it actually pops up the
  preferences.

* Make sure the icon is actually updated every hour.  Try using
  AlarmManager.setInexactRepeating(ELAPSED_REALTIME, now+15m, INTERVAL_HALF_HOUR)

  Testing in emulator since 15:21, verify that updates seem to come in
  as expected.  They do.  Skeppa.

  Done:
  * Ditch the system-provided repeated invocation of the
    AppWidgetProvider.
    I set the updatePeriodMillis to 0 according to this post:
    http://knowledgefolders.com/akc/display?url=DisplayNoteIMPURL&reportId=3299&ownerUserId=satya
  * Register the alarm on first startup.
  * AlarmManager.cancel() the alarm at shutdown.

* Use an Alarm to update every 30 minutes, but don't wake the phone
  for the update.

* Use wget to evaluate different retry schemes at a time when the
  geonames.org servers are heavily loaded.

* Make sure the widget displays something even before the first
  weather observation has been fetched / failed.

* Add a preference for enabling the weather station information.

* Verify the widget in the 1.5 emulator.

* Make two instructive screenshots on how to add the widget.

* Publish on Launchpad.

* Publish on Market.

* Change compatibility so that we show up on Market for new devices as
  well.

* Make sure "Network location disabled" shows up if network
  locationing is disabled.

* If network locationing is disabled, clicking the icon should bring
  up the system locationing preferences.

* Make text color configurable.  Make a custom preference:
  http://www.kaloer.com/android-preferences
  Use the OpenIntents color picker.
  On ActivityNotFoundException, open a dialog saying "Color picker not
  installed, install from Market now?".  On "yes", open the color
  picker in Market using a market:// URL.  If Market cannot be
  launched, display an error dialog.

* När windchill används, byt temperaturtecknet mot en upphöjd
  asterisk.

* If WiFi becomes available, think about whether we should update the
  weather then.  This is important for users without a data plan.

* When any network becomes available, from having no network, update
  the weather.  Maybe limit this to twice per hour.

* Make an icon.

* Add a "Report Problem" button to the log viewer.

* Add device info, consisting of all fields of the Build class to the
  problem reporting e-mail.

* Change the View Logs preferences button into a tabbed interface with
  the first tab containing preferences and the second the logs.

* Populate the log view in the background and don't enable the report
  problem button until the log view has been fully populated.

* Integrate with SendLog and put a "Report Problem" button in the
  preferences.

* Make a layouts-w100mm wide-screen layout for the Actions activity to
  work better on really wide screens (tablets and large phones in
  landscape mode).

* Add info to problem reports to help us identify task killers.

* Add info to problem reports to help us reason about battery usage

* Write an FAQ about the need for Google Play, then link to that FAQ
  on devices that don't have it.
