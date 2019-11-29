package com.londogard.summarize

import org.junit.Assert.assertEquals
import org.junit.Test

class MyLibraryTest {
    @Test fun testTokenization() {
        val summarizer = Summarize(SummarizeVariant.TfIdf)
        val summary = summarizer.summarize("""CNN) - Life exists in extreme environments on Earth, from arid deserts and frozen tundras to thermal, toxic vents in the deepest reaches of the ocean floor. But it can't exist on every inch of the planet and scientists have discovered a place in Ethiopia where life can't find a way, according to a new study.

In contrast with previous research, scientists conducted multiple tests and found that there is no life, not even microorganisms, in Dallol. One of Earth's most extreme environments, Dallol is incredibly hot, salty and acidic. Its ponds extend across a volcanic crater, in the Ethiopian Danakil depression, filled with salt, toxic gases and boiling water in response to extreme hydrothermal activity.

Even in winter, daytime temperatures can exceed 113 degrees Fahrenheit. Some of the hyper acidic and saline pools have negative pH values.

The findings published Friday in the journal Nature Ecology & Evolution.

"After analysing many more samples than in previous works, with adequate controls so as not to contaminate them and a well-calibrated methodology, we have verified that there's no microbial life in these salty, hot and hyperacid pools or in the adjacent magnesium-rich brine lakes," said Purificación López García, study author and biologist at the French National Centre for Scientific Research.

However, outside of the ponds, it's a different story.

"What does exist is a great diversity of halophilic archaea (a type of primitive salt-loving microorganisms) in the desert and the saline canyons around the hydrothermal site, but neither in the hyperacid and hypersaline pools themselves, nor in the so-called Black and Yellow lakes of Dallol, where magnesium abounds," said López García. "And all this despite the fact that microbial dispersion in this area, due to the wind and to human visitors, is intense."

The researchers performed mass sequencing of genetic markers meant to find and classify any microorganisms that may be present, as well as cultures to find microbes, cytometry for detecting individual cells, brine chemical analysis and electron microscopy combined with X-ray spectroscopy.

At first glance, minerals rich in silica may mimic microbial cells, the researchers said. But their analysis revealed the difference.

"In other studies, apart from the possible contamination of samples with archaea from adjacent lands, these mineral particles may have been interpreted as fossilized cells, when in reality they form spontaneously in the brines, even though there is no life," López García said.

Scientists have used evidence of life in extreme environments on Earth as an analog for the conditions where life may exist on other planets in our solar system or beyond it. The researchers warned that in this case, just because there is liquid water present or because something resembles cells or other biological aspects beneath a microscope, does not mean there is life present.

"Our study presents evidence that there are places on the Earth's surface, such as the Dallol pools, which are sterile even though they contain liquid water," López García said.

The Dallol ponds actually prevent life from forming because they contain chemical barriers like chaotropic magnesium salts that help break down hydrogen. Combined with the salty, acidic and hot environment, life receives no encouragement in the pools.

"We would not expect to find life forms in similar environments on other planets, at least not based on a biochemistry similar to terrestrial biochemistry," said López García.

The researchers will continue studying the pools to determine more about the limits of life. """, 0.2)
        assertEquals(summary.split("\n").size, 5)
    }
}

