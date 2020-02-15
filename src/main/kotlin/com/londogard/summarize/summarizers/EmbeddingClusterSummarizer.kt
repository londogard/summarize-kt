package com.londogard.summarize.summarizers

import com.londogard.smile.SmileOperators
import com.londogard.smile.extensions.*
import com.londogard.summarize.embeddings.WordEmbeddings
import com.londogard.summarize.extensions.mutableSumByCols
import com.londogard.summarize.extensions.normalize
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis

enum class ScoringConfig {
    Rosselio,
    Ghalandari
}

internal class EmbeddingClusterSummarizer(
    private val threshold: Double,
    private val simThreshold: Double,
    private val config: ScoringConfig,
    private val keepEmbeddingsInRAM: Boolean = false
) : SmileOperators, SummarizerModel {
    private var embeddings: WordEmbeddings = WordEmbeddings(dimensions = 50)
    private val zeroArray = Array(embeddings.dimensions) { 0f }
    private fun String.simplify(): String = normalize().toLowerCase().words().joinToString(" ")

    private fun getWordsAboveTfIdfThreshold(sentences: List<String>): Set<String> {
        val corpus = sentences.map { it.bag(stemmer = null) }
        val words = corpus.flatMap { bag -> bag.keys }.distinct()
        val bags = corpus.map { vectorize(words, it) }
        val vectors = tfidf(bags)
        val vector = vectors.mutableSumByCols()
        val vecMax = vector.max() ?: 1.0

        return vector
            .map { it / vecMax }
            .mapIndexedNotNull { i, d -> if (d > threshold) words[i] else null }
            .toSet()
    }


    private fun getWordVector(words: List<String>, allowedWords: Set<String>): Array<Float> = words
        .filter(allowedWords::contains)
        .fold(zeroArray) { acc, word -> embeddings.vector(word)?.plus(acc) ?: acc }
        .normalize()

    private fun getSentenceBaseScoring(
        cleanSentences: List<String>,
        rawSentences: List<String>,
        centroid: Array<Float>,
        allowedWords: Set<String>
    ): List<ScoreHolder> =
        cleanSentences
            .map { it.words() }
            .map { words -> getWordVector(words, allowedWords) }
            .mapIndexed { i, embedding ->
                val score = embeddings.cosine(embedding, centroid) // Here we can add further scoring params

                ScoreHolder(i, rawSentences[i], score, embedding)
            }
            .sortedByDescending { it.score }


    /**
     * This scoring compares using the following:
     * 1. Don't have a vector to similar to any vector in the current summary
     * 2. Otherwise just take max that we got by base scoring (comparing single sentence to centroid of document)
     */
    private fun scoreRosellio(
        numSentences: Int,
        scoreHolders: List<ScoreHolder>
    ): List<ScoreHolder> {
        val selectedIndices = mutableSetOf(0)
        val selectedVectors = mutableSetOf(scoreHolders.first().vector)

        return listOf(scoreHolders.first()) + (2..min(numSentences, scoreHolders.size))
            .mapNotNull { _ ->
                scoreHolders.asSequence()
                    .filterNot { selectedIndices.contains(it.i) }
                    .filterNot { score ->
                        selectedVectors
                            .any { selectedVector -> embeddings.cosine(score.vector, selectedVector) > simThreshold }
                    }
                    .firstOrNull()
                    ?.let { maxScore ->
                        selectedVectors.add(maxScore.vector)
                        selectedIndices.add(maxScore.i)

                        maxScore
                    }
            }
    }

    /**
     * This scoring compares using the following:
     * 1. Don't have a vector to similar to any vector in the current summary
     * 2. Create centroid of currSummary + currSen and compare to centroid of whole doc
     */
    private fun scoreGhalandari(
        numSentences: Int,
        centroid: Array<Float>,
        scoreHolders: List<ScoreHolder>
    ): List<ScoreHolder> {
        val selectedIndices = mutableSetOf(scoreHolders.first().i)
        val selectedVectors = mutableSetOf(scoreHolders.first().vector)
        var centroidSummary = scoreHolders.first().vector

        return listOf(scoreHolders.first()) + (2..min(numSentences, scoreHolders.size))
            .mapNotNull { _ ->
                scoreHolders
                    .filterNot { selectedIndices.contains(it.i) }
                    .filterNot { score ->
                        selectedVectors.any { selectedVector ->
                            embeddings.cosine(score.vector, selectedVector) > simThreshold
                        }
                    }
                    .maxBy { score -> embeddings.cosine(centroid, (score.vector + centroidSummary).normalize()) }
                    ?.let { maxScore ->
                        centroidSummary = (centroidSummary + maxScore.vector)
                        selectedVectors.add(maxScore.vector)
                        selectedIndices.add(maxScore.i)

                        maxScore
                    }
            }
    }

    override fun summarize(text: String, lines: Int): String {
        val sentences = text.sentences()
        val superCleanSentences = sentences.map { it.simplify() }
        val wordsOfInterest = getWordsAboveTfIdfThreshold(superCleanSentences)

        if (!keepEmbeddingsInRAM) embeddings = WordEmbeddings(dimensions = 50, inFilter = wordsOfInterest)

        val centroidVector = getWordVector(superCleanSentences.flatMap { it.words() }, wordsOfInterest)
        val scores = getSentenceBaseScoring(superCleanSentences, sentences, centroidVector, wordsOfInterest)
        val finalSentences = when (config) {
            ScoringConfig.Ghalandari -> scoreGhalandari(lines, centroidVector, scores)
            ScoringConfig.Rosselio -> scoreRosellio(lines, scores)
        }

        return finalSentences.sortedBy { it.i }.joinToString("\n") { it.rawSentence }
    }

    override fun summarize(text: String, ratio: Double): String {
        val lines = text.sentences().size

        return summarize(text, (lines * ratio).roundToInt().coerceAtLeast(1))
    }
}

object a {
    @JvmStatic
    fun main(args: Array<String>) {
        val text = """
            Editor's Note: The following piece contains explicit descriptions of sexual abuse allegations that may be disturbing to some readers.

            (CNN) - Emilia Heckman trusted Dr. Robert Hadden.

            A fashion model in her late 20s, she found herself chatting casually about her family and career with the OB-GYN at Columbia University's prestigious hospital system, who showed her photos of his wife and daughters. She could catch him for appointments around her erratic schedule. She received free birth control from him.

            "I felt comfortable asking him any questions I had about my health," Heckman, now 36, said in an interview with CNN. "He was so open."

            But every once in a while, according to her interview and court documents, he would startle her with an inappropriate question or comment, asking about the quality of her sex life, or saying, "Your boyfriend is so lucky to have you."

            She says the comments also came during exams: "He would be crazy to lose you." "You're perfection."

            Heckman said it took a turn from inappropriate to unacceptable in 2012.

            As often, Heckman was the last patient of the day. Hadden told the nurse she could go home; she left reluctantly, looking troubled, Heckman said.

            Now it was just Hadden and Heckman, whose feet were in the stirrups and legs were draped. Hadden dipped his head out of view and licked her, she said.

            "At first it was gloves on, and all of that," Heckman said. "And then it transitioned to no gloves, a tongue and a beard. ... And I recoiled."

            She said she abruptly stepped off the exam table, got dressed, left the office and never returned.

            Heckman said she would later learn that she wasn't the only one.

            Three years later, in 2015, she came across a news story about how Hadden stood accused in a sex-abuse case involving six of his patients. Their allegations had mirrored hers: that Hadden had fondled and sexually assaulted them during examinations, after nurses had left the room. The next year, he pleaded guilty to two counts: criminal sexual act in the third degree and forcible touching.

            It seemed to be case closed.

            But this past January, Evelyn Yang, wife of former Democratic presidential candidate Andrew Yang, came forward in an exclusive CNN interview, saying that she, too, was sexually abused by Hadden. Suddenly the terms of the plea deal sparked fresh outrage: Hadden had surrendered his medical license, but received no prison time, probation or community service.

            A torrent of critics blasted the Manhattan District Attorney's office for what they believe to be a light sentence.

            Since Yang's interview, nearly 40 new Hadden accusers have brought their allegations to attorney Anthony DiPietro, who filed a civil suit against Hadden and Columbia University in 2019. DiPietro says he plans to add them to the civil suit, which would bring the total number of plaintiffs to about 70. Two of the plaintiffs were minors -- ages 15 and 16 -- at the time of the alleged abuse, he said.

            Sexual assault survivors and their supporters held a protest in January saying the Hadden case proves Manhattan District Attorney Cyrus Vance has failed in protecting victims. The New York City Council's women's caucus called for Vance's resignation, saying the Hadden plea deal fits a pattern of lenience by the DA towards wealthy, white men.

            Vance has refused CNN's multiple request for interviews. After CNN notified the office of Evelyn Yang's public allegations last month, Vance said in a statement, "Because a conviction is never a guaranteed outcome in a criminal trial, our primary concern was holding him accountable and making sure he could never do this again -- which is why we insisted on a felony conviction and permanent surrender of his medical license.

            "While we stand by our legal analysis and resulting disposition of this difficult case, we regret that this resolution has caused survivors pain," Vance said.

            His office has encouraged any survivors to call the DA's sex crimes unit.

            Heckman is among dozens of new accusers who want Hadden to be prosecuted again; she says she plans to present her case to the DA directly.

            "I want justice served," said Heckman. "He's raped, molested all these women and nothing's been done and that makes me furious."

            The 61-year-old Hadden has denied the assault allegations in court documents, aside from the two counts to which he pleaded guilty. CNN reached out to Hadden and the attorney who represented him in the civil case; those efforts were unsuccessful.

            "I'm thinking to myself ... I want his hands off of me."

            One of the new accusers is Jessica Chambers, now a substitute elementary-school teacher in Wyoming.

            Chambers was scrolling through her phone last month when she came across the news story about Yang. At the sight of a photo of Hadden in court, she said, her stomach knotted.

            "I was like, 'Holy sh*t,'" she said.

            Though she had seen nurse practitioners at Planned Parenthood, Chambers had never had a gynecological exam when she first stepped into Hadden's office in 2004. She was a 23-year-old student at the City College of New York.

            Her first impression: "He was very present. With some doctors, they just get you in and out. (But) he was just very there with you. ... He was very friendly."

            Chambers said two things initially struck her as odd during the exam, for which a chaperone was present: Hadden was chatty, and the procedure seemed to go on and on.

            "He had his fingers inside of me -- I couldn't see if he was wearing gloves," she said. "And he had an extended conversation with me while he had his fingers inside of me. ... I remember wanting to get out of that position."

            Chambers said they talked about how she'd just broken up with her boyfriend. She remembers Hadden asking her if she was able to climax, "and how was I able to climax," she said.

            "I'm thinking it's very weird," she said. But "he's a doctor, we're in Columbia -- clearly what's going on here must be normal and natural."

            At some point, she said -- while she was sitting on the exam table, and after the chaperone had left -- he grabbed her leg.

            "There was an opening," she said. "Maybe I asked a question, and the question was license to physically show me."

            "He had me somewhat stuck," she said, adding that she felt uncomfortable, but "I didn't know whether it was just me being naive and I didn't want to do anything that would be weird."

            Hadden, she said, began explaining how arousal happens while extensively touching her vagina -- this time with ungloved hands.

            "I mean now, in hindsight, I'm like, he was trying to arouse me while talking to me -- under the guise of education," Chambers said. "I'm thinking to myself, this is enough -- I want his hands off of me. ... And it went on for -- it seemed like an extended period of time."

            Dr. David Shalowitz, who chairs the ethics committee of the American College of Obstetricians and Gynecologists, said allegations like the ones brought forth in the Hadden case are deeply concerning.

            "There are bright lines," he told CNN. "Ungloved rectal or genital exams are not acceptable ever. ... Any exam that's done should be with consent and a clear explanation for why it's being done. Patients have the right to refuse any procedure or exam, and patients should feel empowered to say no."

            Chambers said she would like to see Hadden face trial.

            "When you have this many people coming forward, I feel like you should be held to account again," she said. "And actually be held to account -- like, go to jail."

            DiPietro said all of his clients want the DA to reopen a criminal case against Hadden. He added that he plans to submit documentation to demand that Vance's office do so.

            "He got something that sounds [more] like an orchestrated retirement than an actual sentence for a serial sexual predator," DiPietro said.

            "It's like he knew ... that he was protected."

            Columbia has denied in legal filings the civil suit's allegations that the university did nothing to stop the "serial sexual abuse" on "countless occasions."

            In 2012, Hadden was arrested in his office after a patient told police that he had licked her vagina during an exam. The arrest was voided, and Hadden returned to his job at the medical clinic for more than a month. During that time, he allegedly assaulted Yang and at least one other patient.

            "Can you imagine the audacity of a man who continues to do this after being arrested?" Yang said. "It's like he knew that he wouldn't face any repercussions. That he was protected. That he wouldn't be fired."

            Columbia University responded to CNN's request for comment with a statement on Thursday, saying, "Nothing is more important to us than the safety of our patients. We are committed to treating every patient with respect and delivering care to the highest professional standards.

            "We condemn sexual misconduct in any form and extend our deepest apologies to the women whose trust Robert Hadden violated and to their families."

            Another accuser, who asked not to be named out of privacy concerns, said she was pregnant with her eldest when she went to see Hadden around 2002. She'd recently had a failed pregnancy while seeing a doctor at another hospital and didn't want to take chances. Thinking the prestigious New York-Presbyterian Hospital/Columbia University Medical Center to be a safe bet, she went there and found Hadden based on a recommendation.

            Hadden, she said, asked her to come in to see him about once a month and gave her a vaginal and breast exam on almost every visit.

            "The breast exam was an excessive massage," she said.

            She remembers him asking strange questions, most notably if she enjoyed sex. In the midst of her pregnancy, the accuser said, she called a female friend who works as an OB-GYN to ask if the frequent breast and vaginal exams were normal.

            "Absolutely not," she recalled the friend saying. "That's weird."

            But she continued to go in, she said, because she didn't want to start over with another doctor, knowing through firsthand experience how fragile a pregnancy can be and wanting the best for her baby.

            "You feel the health of the child is in his hands," she said.

            She said she stopped seeing Hadden shortly after he delivered the baby.

            She, too, learned of other allegations only after seeing Yang's interview this past January, more than 16 years later.

            "I felt so validated," she told CNN, adding that Hadden's sentence "did not match the crime."

            "It wasn't even a slap on the wrist," she said.

            Hadden's 2016 plea deal is 'inexplicable'

            As a result of Hadden's 2016 plea deal, the doctor also registered as the lowest-level sex offender. This defied the recommendation of the State of New York Board of Examiners of Sex Offenders to label Hadden a Level 2 -- or moderate -- offender, which would require his inclusion in an online sex-offender registry for life.

            Elie Honig, a former federal and state prosecutor and a CNN legal analyst, called the 2016 agreement "inexplicable."

            "I cannot think of any legitimate reason why you would give this guy a plea deal that would not put him behind bars for one day," he said. "It is unjust."

            Whether Hadden can be tried on new criminal charges depends on the circumstance of each accuser. A provision in the Hadden plea agreement stipulates that the DA cannot prosecute any "similar crimes" against the doctor that the office knew about on or before February 22, 2016 -- the day the deal was struck.

            Honig called this clause "unusual," adding that it precludes women who served as witnesses or called the DA prior to that date from pursuing charges even if new evidence emerges.

            "The DA's office essentially said we're only going to prosecute for a few of you," Honig said. "The rest of you, sorry, we're giving it away."

            There is also uncertainty around the issue of statute of limitations for the new accusers who have come forward: While New York has recently expanded the timeframe allowing victims to come forward with some sex crimes allegations, it's unclear how that would impact the new accusations leveled against Hadden.

            Heckman said she first told her husband, media executive James Heckman, about the incident while on their honeymoon in Italy during the summer of 2015.

            That night, at her husband's urging, they searched for Hadden's name on Google; that is how they learned of the criminal case against the doctor.

            Heckman joined DiPietro's civil suit as a Jane Doe but decided to come forward with her full name after seeing Yang's interview.

            "I think the more victims come out and show their face -- 'Hey I'm a real person, I'm not just Jane Doe,' you know, maybe the DA will listen to that," Heckman said. "It's just like, we're real people, we're not just a piece of paper."
        """.trimIndent()
        val summarizer = SummarizerModel.embeddingClusterSummarizer(keepEmbeddingsInRAM = true)
        println(measureTimeMillis { (1..10).forEach { summarizer.summarize(text, 10) } })
    }
}