# SkinProof

SkinProof is a measured skincare-product experiment tracker. It is designed to report cosmetic trends with uncertainty, not to provide medical advice.

## Current implementation

- React experiment setup, listing, archive workflow, and a consent-gated Camera Kit/API test view.
- Spring Boot REST API with PostgreSQL persistence, browser-profile demo scoping, Swagger UI, and an opt-in YouCam SD analysis spike at `/api/analysis/skin`.
- AWS deployment foundation is being added; no live AWS environment has been provisioned yet.
- SD concern identifiers follow the published YouCam docs; live API behavior has not yet been tested. The API key is read from `YOUCAM_API_KEY` and must remain server-side.

## Hosted demo

The intended demo runtime is AWS-hosted. It will not require a developer machine to run while the demo is in use. Infrastructure and automated deployment are being prepared; this repository does not yet contain a live demo URL.

## Privacy and demo data

The current workspace identifier separates records between browser profiles, but it is not authentication. Do not enter sensitive personal information in this initial demo. SkinProof results are cosmetic trends, not medical advice. Simulated data, when added, will be labeled in the interface.

## Development checks

The frontend production bundle can be checked with `npm ci && npm run build` from `frontend/`. The backend uses Java 21 and Gradle 8.12.1; run `gradle -p backend test` for its tests. CI installs the toolchains and runs both checks. Running the application on a developer machine is not required for the hosted demo.

See [docs/api-notes.md](docs/api-notes.md) for the documented API contract and the remaining live-test checklist.