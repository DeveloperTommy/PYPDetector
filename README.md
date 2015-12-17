#Poop Your Pants Detector 
##Tommy Nguyen and David Nguyen

##Introduction
Our goal is to detect stress levels while playing horror video games and classify between levels of fright and jump scares. The motivation behind this classification problem is to be able to see in real time how scared a person is feeling. This can be particularly useful for video game streamers who want to entertain their audience and give them insight to how that streamer is feeling while playing the video game.  

##Features
-Visualize heartbeat and RR readings (RR readings are magnified 100x for scale)
-State levels of fear in real time {calm, anxious, scared, very scared, terrified (equivalent to the level of a jump scare)}
-Record video of game and reactions
-Video saved to /data/com.example.david.pyp/files
-Record GSR, heart rate, and RR intervals
-Live graph of heart rate and RR intervals
--Screenshot of graph saved to /sdcard/’DATE mm-dd.jpg’
-Allows for logging of GSR, heart rate, and RR intervals
-Screenshots visualization and saves them to device when logging values
--Saved to /sdcard/mysdfile.txt

##Data Collection + Analysis
We collected data in total of 7 males and 4 females. We labeled their data based on GSR, heart rate and RR intervals. Some of the things we found while collecting the data were:

Unexpectedly, jump scares did not increase heart beat. Instead, they caused a sharp drop in the GSR (for resistance). That seemed to be the only feature to indicate a jump scare
Some of the girls tended to close their eyes when they got very scared, leading to lower heart beat and higher GSR resistance during the scary parts of the game.
Heartbeat provided significant amounts of information for someone’s level of fear other than being terrified/jumpscared. For example, significant increases indicated accurately, a rise in a level of fear
GSR readings provided a reliable anchor for a level of fear when heartbeat would fail
Significant dips when the person got more scared
Unfortunately, GSR resistance only seemed to rise minimally when the person calmed. This could be mitigated in future versions
Heart rate coupled with GSR gave good readings for levels of fear

Originally, we planned to take all our our training data and train it through Weka to build a classifier. However, due to time constraints as well as misclassifying from Weka with a wide variety of data points for when one was feeling scared (one person’s readings may indicate that they were calm while for another, that would indicate scared), a handwritten algorithm was written instead. The algorithm takes into mind the trends described above. Details on the algorithm can be found in the source code.

Some screenshots of the data we have are:
 
This was while we were tweaking our algorithm for levels of fear. We used a lot of logging on the laptop to see the fear levels.


This is a screenshot of a live gameplay after a jump scare. We stopped the game a couple of minutes after the jump scare to get a feel for the readings.

##Possible Improvements
With more time, we could build individual profiles of classifiers from Weka that would allow for more accurate readings of fear levels. We could also measure more features such as skin temperature, gyroscope, and facial detection features (such as using Affectiva’s facial emotion API’s). 

##Conclusion
All in all, our project was enjoyable. Our application classifies levels of fear decently well and works fairly well for scary video clips as well. Watching our friends play game was entertaining and it was interesting being able to see their heartbeat as well as GSR readings rise and drop. 

A video of people’s reactions can be seen here:
https://www.youtube.com/watch?v=YTL4emr40hk&feature=youtu.be


