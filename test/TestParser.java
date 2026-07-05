import com.moviearchive.parser.MovieNameParser;
import com.moviearchive.parser.MovieNameParser.ParseResult;

public class TestParser {
    public static void main(String[] args) {
        String[] samples = {
            "What Planet Are You From 2000 720p Blu Ray_i Movie D L..mkv",
            "What Still Remains 2018 720p Blu Ray_i Movie D L..mkv",
            "What We Started 2017 720p W E B D L_i Movie D L..mkv",
            "What Will People Say 2017 720p Blu Ray_i Movie D L..mkv",
            "When Angels Sleep 2018 720p Blu Ray_i Movie D L..mkv",
            "When Calls The Heart 2013 720p W E B D L_i Movie D L..mkv",
            "When The Day Comes 2017 720p Blu Ray_i Movie D L..mkv",
            "When We Leave 2010 720p_ I M D B D L. Com..mkv",
            "White  God (2014) 720p..mkv",
            "White Christmas 1954 720p Blu Ray_i Movie D L. Com Exclusive..mkv",
            "White Fang 2018 720p Blu Ray_i Movie D L..mkv",
            "Who 1974 720p Blu Ray_i Movie D L..mp4",
            "Widows 2018 1080p Blu Ray M C_i Movie D L..mkv",
            "Wild Bill 1995 720p Blu Ray_i Movie D L..mkv",
            "Wild Horses 2015 720p Blu Ray_i Movie D L..mkv",
            "Wilde 1997 720p_ Imovie Dl. Com..mp4",
            "Williams 2017 720p Blu Ray_i Movie D L..mp4",
            "Winchester73 1950 720p W E B D L_i Movie D L. Com Exclusive..mkv",
            "Wind Chill 2007 720p Blu Ray_i Movie D L..mkv",
            "Wings Of The Wind 2015 720p W E B Rip_i Movie D L..mkv",
            "Witness To Murder 1954 720p Blu Ray_i Movie D L..mkv",
            "Wolf Mother 2016 720p Blu Ray_i Movie D L..mkv",
            "Wonderland 2015 720p W E B D L_i Movie D L..mkv",
            "Woodlawn 2015 720p_i Movie D L. Com..mkv",
            "Woton'S Wake 1962 D V D Rip_i Movie D L..mkv",
            "Wwe You Think You Know Me The Story Of Edge 2012 1080p Blu Ray_i Movie D L..mp4 - Shortcut.lnk",
            "X Ray 1981 720p Blu Ray_i Movie D L..mp4",
            "X The Man With The X Ray Eyes 1963 720p_ I M D B D L. Com..mp4",
            "X Y And Zee 1972 720p H D T V_i Movie D L..mkv",
            "XXY 2007 720p_ I M D B D L. Com..mkv",
            "Yardie 2018 720p Blu Ray_i Movie D L..mkv",
            "You Ve Been Trumped 2011 720p W E B D L_i Movie D L..mkv",
            "Young Billy Young 1969 720p Blu Ray_i Movie D L..mp4",
            "Young Guns 1988 720p_i Movie D L. Com..mp4",
            "Young Sherlock Holmes 1985 720p W E B D L_i Movie D L..mp4",
            "Zoe 2018 720p Blu Ray_i Movie D L..mkv",
            "Wet Hot American Summer 2001 720p_ I M D B D L. Com..mp4",
            "What Just Happened 2008 720p Blu Ray_i Movie D L..mp4",
            "What Keeps You Alive 2018 720p Blu Ray_i Movie D L..mkv",
            "Lucky Trouble (2011) 720p",
            "Irma la Douce (1963) 720p",
            "Jack And Jill (2011) 720p",
            "Jack Irish-Bad Debts (2012) 720p",
            "Jackass Presents- Bad Grandpa (2013) 720p",
            "Jackie Brown (1997) 720p",
            "Jerry Maguire (1996) 720p",
            "JFK (1991) 720p",
            "Jobs (2011) 720p",
            "Jug Face (2013) 720p",
            "Juno (2007) 720p",
            "Just Married (2003) 720p",
            "Kate & Leopold (2001) 720p",
            "Keeping Mum (2005) 720p",
            "Kelly And Victor (2012) 720p",
            "Killer Joe (2011)-7200p",
            "Killers (2010) 720p",
            "Killing Them Softly (2012) 720p",
            "King Of Thieves (2018) 720p",
            "Kiss Me (2011) 720p",
            "Kiss Me Deadly (1955) 720p",
            "Kiss of the Damned (2012) 720p",
            "Kiss of the Dragon (2001) 720p",
            "Kon-Tiki (2012) 720p",
            "La Caraa (The Hidden Face) (2011) 720p",
            "Ladder 49 (2004) 720p",
            "Las acacias (2011) 720p",
            "Last Love (2013) 720p",
            "Last Vegas (2013) 720p",
            "Last Year at Marienbad (1961) 720p",
            "Lawrence of Arabia (1962) 720p",
            "Le Cercle Rouge (1970) 720p",
            "Le Havre (2011) 720p",
            "Le mepris (1963) 720p",
            "Legendary Assassin (2008) 720p",
            "Letters to Juliet (2010) 720p",
            "Liberal Arts (2012) 720p",
            "Life is sweet (1990) 720p",
            "Lifeboat (1944) 720p",
            "Like Crazy (2011) 720p",
            "Like Someone in Love (2012) 720p",
            "Lilly the Witch The Dragon and the Magic Book (2009)  DVDRip",
            "Lincoln (2012) 1080p",
            "Little Big Man (1970) 720p",
            "Little Boy (2015) 720p",
            "Little Buddha (1993) 720p",
            "Little Fockers (2010) 720p",
            "Live Flesh (1997) 720p",
            "Loosies (2012) 720p",
            "Love And Death (1975) 720p",
            "Love Is A Many Splendored Thing (1955) 720p",
            "Love Story (1970) 720p"
        };

        MovieNameParser parser = new MovieNameParser();
        int ok = 0, flagged = 0;
        for (String s : samples) {
            ParseResult r = parser.parse(s);
            String flag = "";
            if (!r.yearFound()) { flag = "  <<< NO YEAR"; flagged++; }
            else if (r.isShortcut()) { flag = "  <<< SHORTCUT"; }
            else ok++;

            System.out.printf("%-45s | %-4s | alt=%-20s%s%n",
                    r.cleanTitle(),
                    r.year() == null ? "-" : r.year(),
                    r.alternateTitle() == null ? "-" : r.alternateTitle(),
                    flag);
        }
        System.out.println("\n---");
        System.out.println("Total: " + samples.length + " | Year found: " + (samples.length - flagged) + " | Flagged: " + flagged);
    }
}
