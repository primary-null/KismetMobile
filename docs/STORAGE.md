# Storage — AppPrefs

`storage/AppPrefs.kt` — thin SharedPreferences wrapper. All data serialised as JSON via Gson.

## Keys

| Key constant | Type stored | Accessor methods |
|---|---|---|
| `KEY_PROFILES` | `List<ConnectionProfile>` | `getProfiles()`, `saveProfile()`, `deleteProfile(id)` |
| `KEY_CAMPAIGNS` | `List<Campaign>` | `getCampaigns()`, `saveCampaigns()`, `createCampaign()`, `updateCampaign()`, `deleteCampaign(id)` |
| `KEY_WIGLE` | `WigleProfile` | `getWigleProfile()`, `saveWigleProfile()` |

## Migration Safety

All `getCampaigns()` and `getProfiles()` deserialization is wrapped in `try-catch` returning `emptyList()`.  
This handles schema evolution — old JSON silently fails and the user re-creates data rather than crashing.

## Campaign helpers

```kotlin
AppPrefs.createCampaign(name, type, description): Campaign
// Creates with UUID, DRAFT status, empty deviceKeys/firedAlerts

AppPrefs.updateCampaign(campaign)
// Replaces by ID in the stored list

AppPrefs.addDeviceToCampaign(campaignId, deviceKey)
// Finds campaign by ID, appends key if not already present

AppPrefs.deleteCampaign(id)
// Removes by ID from stored list
```

## WiGLE Profile

```kotlin
AppPrefs.getWigleProfile(): WigleProfile   // returns WigleProfile("","",false) if unset
AppPrefs.saveWigleProfile(profile)
```

Fields are set in SettingsFragment and consumed in CampaignDetailFragment's `uploadToWigle()`.

## Notes

- SharedPreferences name: `"kismet_prefs"` (default — check AppPrefs constructor)
- No encryption: credentials and private keys are stored in private app preferences. Cloud backup and device transfer of preferences and internal files are disabled. Device compromise can still expose them. SSH first-use pins are stored under private internal files; clearing app data removes them along with profiles and campaigns.
- ViewModel always calls `refreshProfiles()` / `refreshCampaigns()` after mutating AppPrefs  
  so LiveData stays in sync with on-disk state.
