const functions = require("firebase-functions");
const {google} = require("googleapis");
const cors = require("cors")({origin: true});

const oAuth2Client = new google.auth.OAuth2(
    functions.config().google.client_id,
    functions.config().google.client_secret,
    "https://us-central1-the-big-6ix.cloudfunctions.net/" +
  "exchangeAuthCodeForTokenAndCheckMembership",
);

exports.exchangeAuthCodeForTokenAndCheckMembership =
  functions.https.onRequest((req, res) => {
    cors(req, res, async () => {
      const authCode = req.body.authCode;

      if (!authCode) {
        return res.status(400).send({
          error: "Authorization code is required",
        });
      }

      try {
        const {tokens} = await oAuth2Client.getToken(authCode);
        oAuth2Client.setCredentials(tokens);

        const scopesGranted = tokens.scope ? tokens.scope.split(" ") : [];
        const requiredScope =
          "https://www.googleapis.com/auth/youtube.channel-memberships.creator";

        if (!scopesGranted.includes(requiredScope)) {
          return res.status(403).send({
            error: "Required scope not granted: " +
                   "youtube.channel-memberships.creator",
          });
        }

        const youtube = google.youtube({
          version: "v3",
          auth: oAuth2Client,
        });

        const response = await youtube.subscriptions.list({
          part: "snippet",
          mine: true,
        });

        const subscriptions = response.data.items || [];
        const isSubscribed = subscriptions.some((sub) =>
          sub.snippet.resourceId.channelId ===
          "UCUP5RcljxXkKm3agi9WNrDA",
        );

        if (isSubscribed) {
          return res.status(200).send({isMember: true});
        } else {
          return res.status(403).send({
            isMember: false,
            message: "Not a channel member",
          });
        }
      } catch (error) {
        console.error("Error getting tokens:", error);
        return res.status(500).send({
          error: "Error getting tokens",
          details: error.message,
        });
      }
    });
  });
