onebusaway-voicexml
===================

This is a VoiceXML client for OneBusAway (specifically, OneBusAway with the OBANYC additions to the `TransitDataService`).

While OneBusAway does include an IVR component, it's designed to integrate directly with Asterisk.  It's complex and hard to set up (for example, it has to be integrated with an external text-to-speech component).

Developing with VoiceXML means that much of the scaffolding which complicates the legacy OneBusAway IVR component can instead be offloaded to a VoiceXML platform, whether a hosted service or on-premise product.
